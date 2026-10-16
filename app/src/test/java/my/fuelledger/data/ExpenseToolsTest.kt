package my.fuelledger.data

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.time.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 35], application = android.app.Application::class)
class ExpenseToolsTest {
    private lateinit var db: LedgerDatabase
    private lateinit var repo: LedgerRepository
    @Before fun setup() = runBlocking { db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), LedgerDatabase::class.java).build(); repo = LedgerRepository(db, MemorySettings()); repo.initialize() }
    @After fun close() { db.close() }
    @Test fun refundsReduceSpendingAndRejectOverRefundWithoutChangingIncome() = runBlocking {
        val e = Expense("original", 2000, "food", 1000, merchant = "Cafe")
        repo.saveExpense(e)
        repo.saveExpense(e.copy(id = "refund", cents = -500, kind = "Refund", refundOf = e.id))
        assertEquals(1500L, db.dao().allExpenses().sumOf { it.cents })
        assertTrue(db.financeDao().allIncome().isEmpty())
        assertTrue(runCatching { repo.saveExpense(e.copy(id = "over", cents = -1600, kind = "Refund", refundOf = e.id)) }.isFailure)
        assertEquals(2, db.dao().allExpenses().size)
    }
    @Test fun undoSurvivesRepositoryRecreationAndRefusesToOverwriteNewEdits() = runBlocking {
        val e = Expense("e", 1200, "food", 1000)
        repo.saveExpense(e); repo.deleteExpense(e.id)
        val entry = db.toolsDao().allUndo().single()
        LedgerRepository(db, MemorySettings()).undoTransaction(entry.id)
        assertEquals(e, db.dao().expense(e.id))
        repo.saveExpense(e.copy(cents = 1300))
        val previous = db.toolsDao().allUndo().single()
        repo.saveExpense(e.copy(cents = 1400))
        assertTrue(runCatching { repo.undoTransaction(previous.id) }.isFailure)
        repo.undoTransaction(db.toolsDao().allUndo().first { it.change().after?.cents == 1400L }.id)
        assertEquals(1300L, db.dao().expense(e.id)!!.cents)
    }
    @Test fun favouritesAndMerchantMemoryRoundTripThroughBackup() = runBlocking {
        val e = Expense("e", 1500, "food", 1000, "Lunch", payment = "Card", merchant = "Cafe")
        repo.saveExpense(e); repo.saveFavourite(favourite("f", "Lunch", e))
        val snapshot = repo.backup(); snapshot.validate(); repo.restore(snapshot)
        assertEquals("Lunch", db.toolsDao().allFavourites().single().name)
        assertEquals("Card", db.receiptAutomationDao().allMerchants().single().payment)
        assertEquals("Lunch", db.receiptAutomationDao().allMerchants().single().description)
    }
    @Test fun duplicateRulesAndSearchDoNotInventMatches() {
        val e = Expense("e", 1000, "food", 1000, "Lunch", payment = "Credit Card", merchant = "Cafe")
        assertEquals(1, likelyDuplicates(e.copy(id = "new", timestamp = 1100), listOf(e)).size)
        assertTrue(likelyDuplicates(e.copy(id = "new", merchant = "Other cafe"), listOf(e)).isEmpty())
        assertTrue(matchesExpense(e, "Cafe 10.00 card", defaultCategories, ZoneOffset.UTC))
        assertFalse(matchesExpense(e, "groceries", defaultCategories, ZoneOffset.UTC))
    }
    @Test fun calendarUsesLocalDatesAndNeverAddsScheduledPayments() {
        val zone = ZoneId.of("Asia/Kuala_Lumpur"); val day = LocalDate.of(2026, 2, 1)
        val e = Expense("e", 1000, "food", day.atStartOfDay(zone).toInstant().toEpochMilli())
        val summary = calendarSummary(listOf(e), YearMonth.of(2026, 2), zone, day.plusDays(3))
        assertEquals(1000L, summary.total); assertEquals(day, summary.highestDay); assertEquals(250.0, summary.dailyAverageSen, .001)
        assertEquals(0L, calendarSummary(listOf(e), YearMonth.of(2026, 1), zone, day).total)
    }
    @Test fun versionFourMigrationPreservesExpensePaymentCategoryAndAddsOnlyDefaults() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val name = "migration-four-five.db"; context.deleteDatabase(name)
        val file = context.getDatabasePath(name); file.parentFile!!.mkdirs()
        val schema = JSONObject(File("schemas/my.fuelledger.data.LedgerDatabase/4.json").readText()).getJSONObject("database")
        SQLiteDatabase.openOrCreateDatabase(file, null).use { old ->
            val entities = schema.getJSONArray("entities")
            repeat(entities.length()) { index -> val entity = entities.getJSONObject(index); old.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", entity.getString("tableName"))); val indices = entity.optJSONArray("indices") ?: org.json.JSONArray(); repeat(indices.length()) { old.execSQL(indices.getJSONObject(it).getString("createSql").replace("\${TABLE_NAME}", entity.getString("tableName"))) } }
            old.execSQL("INSERT INTO categories VALUES ('food','Food & Drinks','food',4293495383,0)")
            old.execSQL("INSERT INTO expenses(id,cents,categoryId,timestamp,description,notes,payment) VALUES ('old',1234,'food',1000,'Old lunch','Keep me','Credit Card')")
            old.version = 4
        }
        val migrated = Room.databaseBuilder(context, LedgerDatabase::class.java, name).addMigrations(LedgerDatabase.MIGRATION_4_5).build()
        try { val e = migrated.dao().expense("old")!!; assertEquals(1234L, e.cents); assertEquals("Credit Card", e.payment); assertEquals("Expense", e.kind); assertEquals("", e.merchant); assertNull(e.refundOf); assertEquals(5, migrated.openHelper.readableDatabase.version) } finally { migrated.close(); context.deleteDatabase(name) }
    }
}
