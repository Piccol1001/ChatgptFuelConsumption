package my.fuelledger.data

import androidx.room.Room
import android.database.sqlite.SQLiteDatabase
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
import org.json.JSONObject
import java.io.File

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
    @Test fun optionalRefuelMetadataRoundTripsInVersionThreeBackupAndCsv() = runBlocking {
        val refuel = fuel().copy(fullTank = false, station = "Petronas", fuelType = "RON 95",
            stationLocation = "Jalan Ampang, Kuala Lumpur", receiptFile = "12345678-abcd-1234-abcd-123456789abc.jpg")
        repo.saveRefuel(refuel)
        val saved = backupJson.decodeFromString<Backup>(backupJson.encodeToString(repo.backup()))
        assertEquals(3, saved.version)
        saved.validate()
        repo.deleteRefuel(refuel.id)
        repo.restore(saved)
        assertEquals(refuel, db.dao().allRefuels().single())
        assertEquals(saved.expenses, db.dao().allExpenses())
        val csv = repo.exportRefuels()
        assertTrue(csv.contains("\"Fuel type\",\"Station location\",\"Receipt file\""))
        assertTrue(csv.contains("\"RON 95\",\"Jalan Ampang, Kuala Lumpur\",\"12345678-abcd-1234-abcd-123456789abc.jpg\""))
    }
    @Test fun versionOneJsonBackupRestoresWithEmptyMetadataAndIntactFuelLink() = runBlocking {
        repo.saveRefuel(fuel().copy(station = "Original station", notes = "Original notes"))
        val old = JSONObject(backupJson.encodeToString(repo.backup())).apply {
            put("version", 1)
            getJSONArray("refuels").getJSONObject(0).apply {
                remove("fuelType"); remove("stationLocation"); remove("receiptFile")
            }
        }
        val saved = backupJson.decodeFromString<Backup>(old.toString())
        assertEquals(1, saved.version)
        saved.validate()
        repo.deleteRefuel("a")
        repo.restore(saved)
        assertEquals(saved.refuels, db.dao().allRefuels())
        assertEquals(saved.expenses, db.dao().allExpenses())
        assertEquals("", db.dao().allRefuels().single().fuelType)
        assertEquals("", db.dao().allRefuels().single().stationLocation)
        assertNull(db.dao().allRefuels().single().receiptFile)
        assertEquals(3, repo.backup().version)
    }
    @Test fun invalidReceiptNamesAndOversizedMetadataNeverChangeExistingRecords() = runBlocking {
        repo.saveRefuel(fuel())
        val before = repo.backup()
        val invalidNames = listOf("../private.jpg", "/tmp/receipt.jpg", "receipt.jpg", "",
            "12345678-ABCD-1234-abcd-123456789abc.jpg", "12345678-abcd-1234-abcd-123456789abc.JPG",
            "12345678-abcd-1234-abcd-123456789abc.jpg/../other")
        val invalid = invalidNames.map { fuel().copy(receiptFile = it) } +
            listOf(fuel().copy(fuelType = "x".repeat(51)), fuel().copy(stationLocation = "x".repeat(201)))
        invalid.forEach { bad ->
            try { repo.saveRefuel(bad); fail("Expected rejection") } catch (_: IllegalArgumentException) {}
            try { repo.restore(before.copy(refuels = listOf(bad))); fail("Expected rejection") } catch (_: IllegalArgumentException) {}
            assertEquals(before.refuels, db.dao().allRefuels())
            assertEquals(before.expenses, db.dao().allExpenses())
        }
    }
    @Test fun partialFillRepositoryEditsAndDeletesRecalculateAndKeepExpenseLinks() = runBlocking {
        val a = fuel().copy(fullTank = false)
        val b = fuel("b", 2000, 1200.0).copy(fullTank = false, litres = 20.0, cents = 4100)
        val c = fuel("c", 3000, 1600.0).copy(fullTank = false)
        listOf(a, b, c).forEach { repo.saveRefuel(it) }
        val originalLinks = db.dao().allExpenses().associate { it.fuelId to it.id }
        repo.saveRefuel(b.copy(odometer = 1300.0, litres = 10.0, fuelType = "RON 97"))
        val estimates = fillEstimates(db.dao().allRefuels().map { it.point() })
        assertEquals(30.0, estimates[0].kmPerLitre, 0.0)
        assertEquals(7.5, estimates[1].kmPerLitre, 0.0)
        assertEquals(originalLinks, db.dao().allExpenses().associate { it.fuelId to it.id })
        repo.deleteRefuel("b")
        assertEquals(15.0, fillEstimates(db.dao().allRefuels().map { it.point() }).single().kmPerLitre, 0.0)
        assertEquals(setOf("a", "c"), db.dao().allExpenses().map { it.fuelId }.toSet())
    }
    @Test fun schemaOneMigrationRetainsRecordsLinksAndPendingSettings() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val databaseName = "fuel-ledger.db"
        context.deleteDatabase(databaseName)
        val databaseFile = context.getDatabasePath(databaseName).apply { parentFile?.mkdirs() }
        val schemaPath = "schemas/my.fuelledger.data.LedgerDatabase/1.json"
        val schemaFile = listOf(File(schemaPath), File("app/$schemaPath")).first { it.isFile }
        val schema = JSONObject(schemaFile.readText()).getJSONObject("database")
        val originalRefuel = fuel().copy(station = "Old station", notes = "Preserved")
        val originalExpense = Expense("linked", originalRefuel.cents, FUEL_CATEGORY, originalRefuel.timestamp,
            fuelDescription(originalRefuel), originalRefuel.notes, fuelId = originalRefuel.id)
        SQLiteDatabase.openOrCreateDatabase(databaseFile, null).use { old ->
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                val table = entity.getString("tableName")
                old.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                entity.optJSONArray("indices")?.let { indices ->
                    for (j in 0 until indices.length()) old.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
                }
            }
            val setup = schema.getJSONArray("setupQueries")
            for (i in 0 until setup.length()) old.execSQL(setup.getString(i))
            defaultCategories.forEach { category ->
                old.execSQL("INSERT INTO categories VALUES (?, ?, ?, ?, ?)",
                    arrayOf<Any?>(category.id, category.name, category.icon, category.color, if (category.custom) 1 else 0))
            }
            old.execSQL("INSERT INTO refuels VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                arrayOf<Any?>(originalRefuel.id, originalRefuel.timestamp, originalRefuel.odometer, originalRefuel.litres,
                    originalRefuel.cents, 1, originalRefuel.station, originalRefuel.notes))
            old.execSQL("INSERT INTO expenses VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                arrayOf<Any?>(originalExpense.id, originalExpense.cents, originalExpense.categoryId, originalExpense.timestamp,
                    originalExpense.description, originalExpense.notes, originalExpense.payment, originalExpense.fuelId))
            old.execSQL("INSERT INTO pending_settings VALUES (1, ?)", arrayOf("pending preferences"))
            old.version = 1
        }
        // Host tests deliberately use Room's platform helper. Production open() always requires
        // SQLCipher and the Android Keystore; it never falls back to plaintext for these tests.
        val migrated = Room.databaseBuilder(context, LedgerDatabase::class.java, databaseName)
            .addMigrations(LedgerDatabase.MIGRATION_1_2, LedgerDatabase.MIGRATION_2_3).build()
        try {
            assertEquals(originalRefuel, migrated.dao().allRefuels().single())
            assertEquals(originalExpense, migrated.dao().allExpenses().single())
            assertEquals(defaultCategories.toSet(), migrated.dao().allCategories().toSet())
            assertEquals("pending preferences", migrated.dao().pendingSettings()?.payload)
            assertEquals(3, migrated.openHelper.readableDatabase.version)
            migrated.dao().deleteRefuel(originalRefuel.id)
            assertTrue(migrated.dao().allExpenses().isEmpty())
        } finally {
            migrated.close()
            context.deleteDatabase(databaseName)
        }
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
        try { repo.restore(saved); fail("Expected storage failure") } catch (_: RestoreSettingsPendingException) {}
        assertNotNull(db.dao().pendingSettings())
        prefs.fail = false
        repo.initialize()
        assertEquals(saved.settings, prefs.settings.first())
        assertEquals(saved.expenses, db.dao().allExpenses())
        assertNull(db.dao().pendingSettings())
    }
    @Test fun versionThreeBackupRestoresIncomeBudgetsRulesAndHistoricalReceiptMetadata() = runBlocking {
        val finance = FinanceRepository(db)
        finance.saveIncome(FinanceIncome("salary", 350000, 1000, "Salary", "Private note"))
        finance.saveBudget(FinanceBudget("overall", "2026-10", 100000))
        finance.saveBudget(FinanceBudget("food-limit", "2026-10", 20000, "food"))
        finance.saveRecurring(FinanceRecurring("rent", "Rent", 80000, "bills", "Monthly",
            "2026-10-01", "2026-10-01", enabled = false, payment = "Debit Card", notes = "Home"))
        val receipt = "12345678-abcd-1234-abcd-123456789abc.jpg"
        repo.saveExpense(Expense("historical", 1500, "food", 1000, "Lunch", receiptFile = receipt,
            recurringRuleId = "deleted-rule", recurringDate = "2026-10-01"))
        repo.saveRefuel(fuel().copy(receiptFile = receipt))
        val snapshot = backupJson.decodeFromString<Backup>(backupJson.encodeToString(repo.backup()))
        assertEquals(3, snapshot.version)
        snapshot.validate()
        repo.restore(snapshot.copy(expenses = emptyList(), refuels = emptyList(), incomes = emptyList(),
            budgets = emptyList(), recurring = emptyList()))
        repo.restore(snapshot)
        val restored = repo.backup()
        assertEquals(snapshot.copy(createdAt = restored.createdAt), restored)
        assertEquals(receipt, restored.expenses.first { it.id == "historical" }.receiptFile)
        assertEquals("deleted-rule", restored.expenses.first { it.id == "historical" }.recurringRuleId)
        assertEquals(receipt, restored.expenses.first { it.fuelId != null }.receiptFile)
    }
    @Test fun oldBackupVersionsDefaultNewFieldsAndReplaceCurrentFinanceOnlyAfterValidation() = runBlocking {
        repo.saveExpense(Expense("old", 2500, "food", 1000))
        val oldJson = JSONObject(backupJson.encodeToString(repo.backup())).apply {
            remove("incomes"); remove("budgets"); remove("recurring")
            getJSONArray("expenses").getJSONObject(0).apply {
                remove("receiptFile"); remove("recurringRuleId"); remove("recurringDate")
            }
        }
        for (version in 1..2) {
            oldJson.put("version", version)
            db.financeDao().putIncome(FinanceIncome("current", 5000, 2000))
            db.financeDao().putBudget(FinanceBudget("current-budget", "2026-10", 2000))
            val legacy = backupJson.decodeFromString<Backup>(oldJson.toString())
            assertTrue(legacy.incomes.isEmpty() && legacy.budgets.isEmpty() && legacy.recurring.isEmpty())
            assertNull(legacy.expenses.single().receiptFile)
            assertNull(legacy.expenses.single().recurringRuleId)
            repo.restore(legacy)
            assertEquals(legacy.expenses, db.dao().allExpenses())
            assertTrue(db.financeDao().allIncome().isEmpty())
            assertTrue(db.financeDao().allBudgets().isEmpty())
            assertEquals(3, repo.backup().version)
        }
    }
    @Test fun invalidFinanceAndOccurrenceMetadataCannotReplaceAnyCurrentRecords() = runBlocking {
        repo.saveExpense(Expense("original", 1000, "food", 1000))
        db.financeDao().putIncome(FinanceIncome("original-income", 100000, 2000))
        val before = repo.backup()
        val income = FinanceIncome("i", 20000, 3000)
        val budget = FinanceBudget("b", "2026-10", 10000, "food")
        val rule = FinanceRecurring("r", "Coffee", 500, "food", "Monthly", "2026-10-01", "2026-10-01", false)
        val occurrence = Expense("occurrence", 500, "food", 3000, recurringRuleId = "deleted-rule", recurringDate = "2026-10-01")
        val invalid = listOf(
            before.copy(incomes = listOf(income, income)),
            before.copy(incomes = listOf(income.copy(cents = -1))),
            before.copy(budgets = listOf(budget, budget.copy(id = "duplicate"))),
            before.copy(budgets = listOf(budget.copy(categoryId = "missing"))),
            before.copy(budgets = listOf(budget.copy(month = "2026-13"))),
            before.copy(recurring = listOf(rule.copy(categoryId = "missing"))),
            before.copy(recurring = listOf(rule.copy(nextDate = "2026-09-01"))),
            before.copy(expenses = listOf(occurrence, occurrence.copy(id = "duplicate-occurrence"))),
            before.copy(expenses = listOf(occurrence.copy(recurringDate = null))),
            before.copy(expenses = listOf(occurrence.copy(recurringDate = "2026-02-30"))),
            before.copy(expenses = before.expenses.map { it.copy(receiptFile = "../private.jpg") })
        )
        invalid.forEach { bad ->
            var rejected = false
            try { repo.restore(bad) } catch (_: Exception) { rejected = true }
            assertTrue("Invalid metadata must be rejected", rejected)
            val after = repo.backup()
            assertEquals(before.copy(createdAt = after.createdAt), after)
            assertNull(db.dao().pendingSettings())
        }
    }
    @Test fun databaseFailureDuringRestoreRollsBackFinanceExpensesAndSettingsIntent() = runBlocking {
        repo.saveExpense(Expense("original", 1000, "food", 1000))
        db.financeDao().putIncome(FinanceIncome("original-income", 100000, 2000))
        val before = repo.backup()
        val replacement = before.copy(expenses = listOf(Expense("blocked", 2000, "groceries", 3000)),
            incomes = listOf(FinanceIncome("replacement-income", 50000, 4000)), settings = AppSettings("3.10", 8.0, "Dark"))
        replacement.validate()
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER test_restore_abort BEFORE INSERT ON expenses WHEN NEW.id = 'blocked' BEGIN SELECT RAISE(ABORT, 'Restore blocked'); END")
        try {
            var rejected = false
            try { repo.restore(replacement) } catch (_: Exception) { rejected = true }
            assertTrue(rejected)
            val after = repo.backup()
            assertEquals(before.copy(createdAt = after.createdAt), after)
            assertNull(db.dao().pendingSettings())
        } finally {
            db.openHelper.writableDatabase.execSQL("DROP TRIGGER test_restore_abort")
        }
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
