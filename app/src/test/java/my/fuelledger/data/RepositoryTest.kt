package my.fuelledger.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import my.fuelledger.domain.*

class MemorySettings : PreferencesStore {
    private val value = MutableStateFlow(AppSettings())
    var fail = false
    override val settings = value.asStateFlow()
    override suspend fun save(settings: AppSettings) { if (fail) error("Storage unavailable"); settings.validate(); value.value = settings }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class RepositoryTest {
    private lateinit var db: LedgerDatabase
    private lateinit var repo: LedgerRepository
    private lateinit var prefs: MemorySettings
    @Before fun setup() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), LedgerDatabase::class.java).build()
        prefs = MemorySettings()
        repo = LedgerRepository(db, prefs)
        repo.initialize()
    }
    @After fun close() { db.close() }
    private fun fuel(id: String = "a", time: Long = 1000, odo: Double = 1000.0) = Refuel(id, time, odo, 40.0, 8200, true)
    @Test fun repeatedInitializationPreservesDataAndSeedsExactlySevenCategories() = runBlocking {
        repo.saveExpense(Expense("e", 1000, "food", 1000))
        repo.initialize()
        assertEquals(7, db.dao().allCategories().size)
        assertEquals(1, db.dao().allExpenses().size)
    }
    @Test fun addingRefuelCreatesExactlyOneLinkedExpenseAndRetryIsIdempotent() = runBlocking {
        val r = fuel()
        repo.saveRefuel(r); repo.saveRefuel(r)
        val e = db.dao().allExpenses().single()
        assertEquals(r.id, e.fuelId)
        assertEquals(r.cents, e.cents)
        assertEquals(FUEL_CATEGORY, e.categoryId)
        assertEquals(1, db.dao().allRefuels().size)
        assertEquals(8200L, db.dao().allExpenses().sumOf { it.cents })
    }
    @Test fun refuelEditSynchronizesAllFieldsWithoutChangingExpenseId() = runBlocking {
        repo.saveRefuel(fuel())
        val old = db.dao().allExpenses().single()
        val updated = fuel().copy(cents = 10000, litres = 50.0, station = "Station", notes = "Notes", timestamp = 2000)
        repo.saveRefuel(updated)
        val e = db.dao().allExpenses().single()
        assertEquals(old.id, e.id); assertEquals(10000L, e.cents); assertEquals(2000L, e.timestamp)
        assertEquals("Refuelling · Station", e.description); assertEquals("Notes", e.notes)
    }
    @Test fun deletingRefuelCascadesOnlyItsLinkedExpense() = runBlocking {
        repo.saveRefuel(fuel())
        repo.saveExpense(Expense("manual", 500, FUEL_CATEGORY, 2000))
        repo.deleteRefuel("a")
        assertTrue(db.dao().allRefuels().isEmpty())
        assertEquals("manual", db.dao().allExpenses().single().id)
    }
    @Test fun linkedExpenseCannotBeChangedOrDeletedIndependently() = runBlocking {
        repo.saveRefuel(fuel())
        val linked = db.dao().allExpenses().single()
        try { repo.saveExpense(linked.copy(fuelId = null)); fail("Expected rejection") } catch (_: IllegalArgumentException) {}
        try { repo.deleteExpense(linked.id); fail("Expected rejection") } catch (_: IllegalArgumentException) {}
        assertEquals(8200L, db.dao().allExpenses().single().cents)
    }
    @Test fun invalidOdometerInsertOrEditLeavesBothTablesUnchanged() = runBlocking {
        repo.saveRefuel(fuel()); repo.saveRefuel(fuel("b", 2000, 1500.0))
        try { repo.saveRefuel(fuel("c", 3000, 1400.0)); fail("Expected rejection") } catch (_: IllegalArgumentException) {}
        try { repo.saveRefuel(fuel().copy(odometer = 1600.0)); fail("Expected rejection") } catch (_: IllegalArgumentException) {}
        assertEquals(2, db.dao().allRefuels().size)
        assertEquals(16400L, db.dao().allExpenses().sumOf { it.cents })
    }
    @Test fun manualExpenseEditAndDeleteChangeAggregations() = runBlocking {
        val e = Expense("e", 25000, "food", 1000)
        repo.saveExpense(e); repo.saveExpense(e.copy(cents = 18000, categoryId = "groceries"))
        assertEquals(18000L, db.dao().allExpenses().single().cents)
        repo.deleteExpense("e"); assertTrue(db.dao().allExpenses().isEmpty())
    }
    @Test fun deletingCustomCategoryReassignsRecordsWithoutDeletingMoney() = runBlocking {
        repo.saveCategory(Category("custom", "Travel", "bag", categoryColors.first()))
        repo.saveExpense(Expense("e", 900, "custom", 1000))
        repo.deleteCategory("custom")
        assertEquals(OTHER_CATEGORY, db.dao().allExpenses().single().categoryId)
        assertEquals(900L, db.dao().allExpenses().single().cents)
    }
    @Test fun defaultCategoryAndDuplicateNameAreProtected() = runBlocking {
        try { repo.deleteCategory(FUEL_CATEGORY); fail("Expected rejection") } catch (_: IllegalArgumentException) {}
        try { repo.saveCategory(defaultCategories.first().copy(name = "Changed")); fail("Expected rejection") } catch (_: IllegalArgumentException) {}
        try { repo.saveCategory(Category("x", "food & drinks", "food", categoryColors.first())); fail("Expected rejection") } catch (_: IllegalArgumentException) {}
        assertEquals(7, db.dao().allCategories().size)
    }
    @Test fun completeBackupRoundTripsRecordsCategoriesAndPreferences() = runBlocking {
        repo.saveRefuel(fuel())
        repo.saveExpense(Expense("e", 3000, "food", 2000, "Lunch, coffee\nmore", "note", "E-wallet"))
        repo.saveSettings(AppSettings("2.15", 6.5, "Dark"))
        val saved = backupJson.decodeFromString<Backup>(backupJson.encodeToString(repo.backup()))
        saved.validate()
        repo.deleteRefuel("a"); repo.deleteExpense("e")
        repo.restore(saved)
        assertEquals(saved.expenses, db.dao().allExpenses())
        assertEquals(saved.refuels, db.dao().allRefuels())
        assertEquals(saved.categories, db.dao().allCategories())
        assertEquals(saved.settings, prefs.settings.first())
        assertNull(db.dao().pendingSettings())
    }
    @Test fun malformedBackupDoesNotTouchExistingRecords() = runBlocking {
        repo.saveRefuel(fuel())
        val old = repo.backup()
        listOf(old.copy(version = 99), old.copy(expenses = emptyList()), old.copy(expenses = old.expenses + old.expenses),
            old.copy(expenses = old.expenses.map { it.copy(cents = 1) }), old.copy(categories = emptyList()),
            old.copy(refuels = old.refuels.map { it.copy(litres = Double.NaN) })).forEach { bad ->
            try { repo.restore(bad); fail("Expected rejection") } catch (_: IllegalArgumentException) {}
            assertEquals(old.expenses, db.dao().allExpenses())
        }
    }
    @Test fun restoreSettingsIntentIsReplayedAfterInterruptedPreferenceWrite() = runBlocking {
        val saved = repo.backup().copy(settings = AppSettings("3.10", 8.0, "Dark"), expenses = listOf(Expense("restored", 1000, "food", 1000)))
        prefs.fail = true
        try { repo.restore(saved); fail("Expected storage failure") } catch (_: IllegalStateException) {}
        assertNotNull(db.dao().pendingSettings())
        prefs.fail = false
        repo.initialize()
        assertEquals(saved.settings, prefs.settings.first())
        assertEquals(saved.expenses, db.dao().allExpenses())
        assertNull(db.dao().pendingSettings())
    }
    @Test fun csvUsesExactMoneyQuotesAndSanitizesSpreadsheetFormulas() = runBlocking {
        repo.saveExpense(Expense("e", 1234, "food", 1000, "=HYPERLINK(\"bad\")", "comma,newline\ntext"))
        val result = repo.exportExpenses()
        assertTrue(result.startsWith("\uFEFF"))
        assertTrue(result.contains("\"12.34\""))
        assertTrue(result.contains("\"'=HYPERLINK(\"\"bad\"\")\""))
        assertTrue(result.contains("\"comma,newline\ntext\""))
    }
    @Test fun invalidExpensesAreRejectedWithoutInsertion() = runBlocking {
        listOf(Expense("e", 0, "food", 1000), Expense("e", -1, "food", 1000), Expense("e", 100, "missing", 1000),
            Expense("e", 100, "food", -1), Expense("e", 100, "food", 1000, payment = "invalid")).forEach { bad ->
            try { repo.saveExpense(bad); fail("Expected rejection") } catch (_: IllegalArgumentException) {}
        }
        assertTrue(db.dao().allExpenses().isEmpty())
    }
}
