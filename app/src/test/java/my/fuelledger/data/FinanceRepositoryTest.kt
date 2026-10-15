package my.fuelledger.data

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.*
import my.fuelledger.domain.MAX_CENTS
import my.fuelledger.domain.Money
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class FinanceRepositoryTest {
    private lateinit var database: LedgerDatabase
    private lateinit var ledger: LedgerRepository
    private lateinit var finance: FinanceRepository
    private val zone = ZoneId.of("Asia/Kuala_Lumpur")

    @Before fun setup() = runBlocking {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), LedgerDatabase::class.java).build()
        ledger = LedgerRepository(database, MemorySettings())
        ledger.initialize()
        finance = FinanceRepository(database)
    }
    @After fun close() { database.close() }

    private fun rule(id: String = "rent", start: String = "2025-01-31", frequency: String = "Monthly",
        categoryId: String = "bills", enabled: Boolean = true) = FinanceRecurring(id, "Scheduled payment", 10_050,
        categoryId, frequency, start, start, enabled)
    private fun timestamp(text: String) = Instant.parse(text).toEpochMilli()

    @Test fun incomeCrudUsesExactSenAndRejectsInvalidMoneyWithoutChangingRecords() = runBlocking {
        val value = FinanceIncome("salary", Money.parse("1200.05"), timestamp("2026-02-01T04:00:00Z"), "Salary")
        finance.saveIncome(value)
        assertEquals(120_005L, finance.dao.allIncome().single().cents)
        finance.saveIncome(value.copy(cents = Money.parse("1200.10")))
        assertEquals(1, finance.dao.allIncome().size)
        assertTrue(runCatching { finance.saveIncome(value.copy(cents = 0)) }.isFailure)
        assertTrue(runCatching { Money.parse("1.001") }.isFailure)
        assertEquals(120_010L, finance.dao.allIncome().single().cents)
        finance.deleteIncome(value.id)
        assertTrue(finance.dao.allIncome().isEmpty())
    }

    @Test fun categoryAndOverallBudgetsUseActualExpensesAndDeviceLocalMonth() = runBlocking {
        val food = Expense("food", 1_001, "food", timestamp("2026-01-31T18:00:00Z")) // February in Malaysia.
        val bills = Expense("bill", 2_002, "bills", timestamp("2026-02-01T01:00:00Z"))
        val january = Expense("old", 4_004, "food", timestamp("2026-01-31T15:00:00Z"))
        listOf(food, bills, january).forEach { ledger.saveExpense(it) }
        val overall = FinanceBudget("overall", "2026-02", 10_000)
        val category = FinanceBudget("food-budget", "2026-02", 2_000, "food")
        finance.saveBudget(overall); finance.saveBudget(category)
        val expenses = database.dao().allExpenses()
        assertEquals(3_003L, financeBudgetSpending(overall, expenses, zone))
        assertEquals(1_001L, financeBudgetSpending(category, expenses, zone))
        assertEquals(2_002L, financeBudgetSpending(overall, expenses, ZoneOffset.UTC))
        val income = listOf(FinanceIncome("pay", 30_001, food.timestamp), FinanceIncome("old-pay", 20_002, january.timestamp))
        assertEquals(30_001L, financeMonthlyIncome(income, YearMonth.of(2026, 2), zone))
        assertEquals(0L, financeMonthlyIncome(income, YearMonth.of(2026, 2), ZoneOffset.UTC))
    }

    @Test fun duplicateBudgetScopeAndMissingCategoryAreRejectedWithoutReplacingExistingBudget() = runBlocking {
        finance.saveBudget(FinanceBudget("overall", "2026-02", 10_000))
        assertTrue(runCatching { finance.saveBudget(FinanceBudget("duplicate", "2026-02", 20_000)) }.isFailure)
        assertTrue(runCatching { finance.saveBudget(FinanceBudget("missing", "2026-02", 20_000, "not-present")) }.isFailure)
        assertEquals(10_000L, finance.dao.allBudgets().single().cents)
        finance.saveBudget(FinanceBudget("overall", "2026-02", 12_000))
        assertEquals(12_000L, finance.dao.allBudgets().single().cents)
        finance.deleteBudget("overall")
        assertTrue(finance.dao.allBudgets().isEmpty())
    }

    @Test fun recurringRuleCrudValidatesScheduleAndStoresFuturePaymentsWithoutPrematureCharges() = runBlocking {
        val value = rule(start = "2999-01-31")
        finance.saveRecurring(value)
        assertTrue(database.dao().allExpenses().isEmpty())
        finance.saveRecurring(value.copy(name = "Updated payment", cents = 5_555, enabled = false))
        assertEquals(1, finance.dao.allRecurring().size)
        assertEquals(5_555L, finance.dao.allRecurring().single().cents)
        assertFalse(finance.dao.allRecurring().single().enabled)
        assertTrue(runCatching { finance.saveRecurring(value.copy(nextDate = "2999-03-28")) }.isFailure)
        assertEquals("Updated payment", finance.dao.allRecurring().single().name)
        finance.deleteRecurring(value.id)
        assertTrue(finance.dao.allRecurring().isEmpty())
    }

    @Test fun missedDailyPaymentsReconcileOnceEvenWithConcurrentRepositoryInstances() = runBlocking {
        finance.dao.putRecurring(rule(start = "2026-01-01", frequency = "Daily"))
        val totals = coroutineScope {
            List(3) { async(Dispatchers.IO) { FinanceRepository(database).reconcile(LocalDate.of(2026, 1, 3), zone) } }.awaitAll()
        }
        assertEquals(3, totals.sum())
        val expenses = database.dao().allExpenses()
        assertEquals(3, expenses.size)
        assertEquals(30_150L, expenses.sumOf { it.cents })
        assertEquals(setOf("2026-01-01", "2026-01-02", "2026-01-03"), expenses.map { it.recurringDate }.toSet())
        assertTrue(expenses.all { it.recurringRuleId == "rent" && it.fuelId == null })
        assertEquals("2026-01-04", finance.dao.allRecurring().single().nextDate)
        assertEquals(0, finance.reconcile(LocalDate.of(2026, 1, 3), zone))
    }

    @Test fun january31AnchorReturnsTo31AfterFebruaryAndSurvivesLeapYears() = runBlocking {
        finance.dao.putRecurring(rule())
        finance.reconcile(LocalDate.of(2025, 3, 31), zone)
        assertEquals(listOf("2025-01-31", "2025-02-28", "2025-03-31"), database.dao().allExpenses().map { it.recurringDate })
        assertEquals("2025-04-30", finance.dao.allRecurring().single().nextDate)
        val anchor = LocalDate.of(2024, 1, 31)
        val february = nextFinanceOccurrence(anchor, anchor, "Monthly")
        assertEquals(LocalDate.of(2024, 2, 29), february)
        assertEquals(LocalDate.of(2024, 3, 31), nextFinanceOccurrence(anchor, february, "Monthly"))
    }

    @Test fun weeklyPaymentsAndDailyDstBoundaryFollowCalendarDatesInsteadOfFixedHours() = runBlocking {
        val newYork = ZoneId.of("America/New_York")
        finance.dao.putRecurring(rule("daily", "2026-03-07", "Daily"))
        finance.dao.putRecurring(rule("weekly", "2026-03-01", "Weekly"))
        finance.reconcile(LocalDate.of(2026, 3, 9), newYork)
        val values = database.dao().allExpenses()
        val daily = values.filter { it.recurringRuleId == "daily" }.sortedBy { it.timestamp }
        assertEquals(listOf("2026-03-07", "2026-03-08", "2026-03-09"), daily.map { Instant.ofEpochMilli(it.timestamp).atZone(newYork).toLocalDate().toString() })
        assertEquals(23 * 3_600_000L, daily[2].timestamp - daily[1].timestamp)
        assertEquals(listOf("2026-03-01", "2026-03-08"), values.filter { it.recurringRuleId == "weekly" }.map { it.recurringDate })
        assertEquals("2026-03-15", finance.dao.allRecurring().single { it.id == "weekly" }.nextDate)
    }

    @Test fun pausedRuleDoesNotChargeAndDeletingRuleRetainsHistoricalExpenses() = runBlocking {
        val rule = rule(start = "2026-01-01", frequency = "Daily", enabled = false)
        finance.dao.putRecurring(rule)
        assertEquals(0, finance.reconcile(LocalDate.of(2026, 1, 2), zone))
        finance.dao.putRecurring(rule.copy(enabled = true))
        assertEquals(2, finance.reconcile(LocalDate.of(2026, 1, 2), zone))
        finance.deleteRecurring(rule.id)
        assertTrue(finance.dao.allRecurring().isEmpty())
        assertEquals(2, database.dao().allExpenses().size)
        assertEquals(0, finance.reconcile(LocalDate.of(2026, 2, 1), zone))
        assertEquals(2, database.dao().allExpenses().size)
    }

    @Test fun retryWithOldCursorPreservesManuallyEditedOccurrencesInsteadOfOverwritingThem() = runBlocking {
        val rule = rule(start = "2026-01-01", frequency = "Daily")
        finance.dao.putRecurring(rule)
        finance.reconcile(LocalDate.of(2026, 1, 1), zone)
        val saved = database.dao().allExpenses().single()
        ledger.saveExpense(saved.copy(cents = 7_777, description = "Adjusted actual payment"))
        finance.dao.putRecurring(rule) // Simulate restoring an older rule cursor with retained transactions.
        assertEquals(0, finance.reconcile(LocalDate.of(2026, 1, 1), zone))
        assertEquals(7_777L, database.dao().allExpenses().single().cents)
        assertEquals("Adjusted actual payment", database.dao().allExpenses().single().description)
        assertEquals("2026-01-02", finance.dao.allRecurring().single().nextDate)
    }

    @Test fun reconciliationFailureRollsBackEarlierPaymentsAndAllCursors() = runBlocking {
        finance.dao.putRecurring(rule("a-valid", "2026-01-01", "Daily"))
        finance.dao.putRecurring(rule("z-invalid", "2026-01-01", "Daily", categoryId = "missing"))
        assertTrue(runCatching { finance.reconcile(LocalDate.of(2026, 1, 2), zone) }.isFailure)
        assertTrue(database.dao().allExpenses().isEmpty())
        assertTrue(finance.dao.allRecurring().all { it.nextDate == "2026-01-01" })
    }

    @Test fun categoryDeletionReassignsRulesAndMergesBudgetsWithoutChangingHistoricalAmounts() = runBlocking {
        val custom = Category("custom-finance", "Local subscriptions", "bill", categoryColors.first(), true)
        ledger.saveCategory(custom)
        finance.saveBudget(FinanceBudget("custom-budget", "2026-02", 3_000, custom.id))
        finance.saveBudget(FinanceBudget("other-budget", "2026-02", 4_000, OTHER_CATEGORY))
        finance.dao.putRecurring(rule(categoryId = custom.id))
        ledger.saveExpense(Expense("past", 1_000, custom.id, timestamp("2026-02-01T00:00:00Z")))
        ledger.deleteCategory(custom.id)
        assertFalse(database.dao().allCategories().any { it.id == custom.id })
        assertEquals(OTHER_CATEGORY, finance.dao.allRecurring().single().categoryId)
        assertEquals(7_000L, finance.dao.allBudgets().single().cents)
        assertEquals(OTHER_CATEGORY, finance.dao.allBudgets().single().categoryId)
        assertEquals(1_000L, database.dao().allExpenses().single().cents)
        assertEquals(OTHER_CATEGORY, database.dao().allExpenses().single().categoryId)
    }

    @Test fun failingBudgetMergeRollsBackCategoryAndRuleChanges() = runBlocking {
        val custom = Category("custom-finance", "Local subscriptions", "bill", categoryColors.first(), true)
        ledger.saveCategory(custom)
        finance.saveBudget(FinanceBudget("custom-budget", "2026-02", MAX_CENTS, custom.id))
        finance.saveBudget(FinanceBudget("other-budget", "2026-02", 1, OTHER_CATEGORY))
        finance.dao.putRecurring(rule(categoryId = custom.id))
        assertTrue(runCatching { ledger.deleteCategory(custom.id) }.isFailure)
        assertTrue(database.dao().allCategories().any { it.id == custom.id })
        assertEquals(custom.id, finance.dao.allRecurring().single().categoryId)
        assertEquals(2, finance.dao.allBudgets().size)
    }
}
