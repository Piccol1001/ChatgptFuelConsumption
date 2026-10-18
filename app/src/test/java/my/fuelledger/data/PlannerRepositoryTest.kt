package my.fuelledger.data

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import my.fuelledger.receipts.parseReceipt
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class PlannerRepositoryTest {
    private lateinit var db: LedgerDatabase
    private lateinit var ledger: LedgerRepository
    private lateinit var finance: FinanceRepository
    private val month = YearMonth.now()
    private fun date(day: Int = 1) = month.atDay(day)
    private fun rule(id: String = "rent", cents: Long = 95000L, kind: String = "Expense") = FinanceRecurring(id, id, cents, if (kind == "Income") "" else "bills", "Monthly", date().toString(), date().toString(), kind = kind, manualPayment = true)
    @Before fun setup() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), LedgerDatabase::class.java).build()
        ledger = LedgerRepository(db, MemorySettings()); ledger.initialize(); finance = FinanceRepository(db)
    }
    @After fun close() { db.close() }
    @Test fun manualFixedAndExpectedIncomeNeverAutoPostAndPaidRetriesDoNotDuplicate() = runBlocking {
        finance.saveRecurring(rule()); finance.saveRecurring(rule("salary", 300000, "Income"))
        finance.reconcile(date(20)); assertTrue(db.dao().allExpenses().isEmpty()); assertTrue(finance.dao.allIncome().isEmpty())
        val occurrence = finance.ensureMonth(month).occurrences().first { it.kind == "Expense" }
        coroutineScope { List(3) { async(Dispatchers.IO) { finance.markPaid(occurrence, date(2), 96000) } }.awaitAll() }
        assertEquals(1, db.dao().allExpenses().size); assertEquals(96000L, db.dao().allExpenses().single().cents)
    }
    @Test fun linkExistingExpenseThenAutomaticRuleCannotCreateSecondPayment() = runBlocking {
        finance.saveRecurring(rule())
        ledger.saveExpense(Expense("existing", 94000, "bills", date().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(), "Rent paid"))
        val due = finance.ensureMonth(month).occurrences().single()
        finance.markPaid(due, date(), 95000, "existing")
        finance.saveRecurring(rule().copy(manualPayment = false))
        finance.reconcile(date(20))
        assertEquals(1, db.dao().allExpenses().size); assertEquals(94000L, db.dao().allExpenses().single().cents)
        assertEquals("rent", db.dao().allExpenses().single().recurringRuleId)
    }
    @Test fun refundCannotBeLinkedAsPaymentOfAFixedCommitment() = runBlocking {
        finance.saveRecurring(rule())
        val refund = Expense("refund", -1000, "bills", 1000, kind = "Refund")
        ledger.saveExpense(refund)
        val due = finance.ensureMonth(month).occurrences().single()
        assertTrue(runCatching { finance.markPaid(due, date(), 95000, refund.id) }.isFailure)
        assertEquals(refund, db.dao().expense(refund.id))
        assertNull(db.dao().occurrence(due.ruleId, due.date))
    }
    @Test fun paymentEditsKeepProvenanceAndDeletingPaymentReopensIt() = runBlocking {
        finance.saveRecurring(rule()); val due = finance.ensureMonth(month).occurrences().single()
        finance.markPaid(due, date(), 95000)
        val paid = db.dao().allExpenses().single()
        ledger.saveExpense(paid.copy(cents = 97000, recurringRuleId = null, recurringDate = null))
        assertEquals("rent", db.dao().allExpenses().single().recurringRuleId)
        ledger.deleteExpense(paid.id); assertNull(db.dao().occurrence("rent", due.date))
        finance.markPaid(due, date(), 98000); assertEquals(98000L, db.dao().allExpenses().single().cents)
    }
    @Test fun pausedEditedDeletedRulesPreservePastMonthlySnapshots() = runBlocking {
        val last = month.minusMonths(1)
        val rule = rule().copy(startDate = last.atDay(1).toString(), nextDate = last.atDay(1).toString())
        finance.saveRecurring(rule)
        val before = finance.ensureMonth(last)
        finance.saveRecurring(rule.copy(cents = 120000, enabled = false))
        assertEquals(before, finance.ensureMonth(last)); assertTrue(finance.ensureMonth(month).occurrences().isEmpty())
        finance.deleteRecurring(rule.id); assertEquals(before, finance.ensureMonth(last))
    }
    @Test fun defaultAllocationChangesPreservePastAndMonthOverrideIsIndependent() = runBlocking {
        val last = month.minusMonths(1)
        finance.ensureMonth(last)
        val old = allocationPlan("default", defaultAllocations)
        finance.saveAllocation(old)
        val changed = listOf(AllocationCategory("reserve", "Savings", 10000, savings = true))
        finance.saveAllocation(allocationPlan("default", changed))
        assertEquals(defaultAllocations, db.plannerDao().allAllocations().single { it.scope == last.toString() }.categories())
        finance.saveAllocation(allocationPlan(month.toString(), defaultAllocations))
        assertEquals(changed, db.plannerDao().allAllocations().single { it.scope == "default" }.categories())
        finance.resetAllocation(month.toString())
        assertFalse(db.plannerDao().allAllocations().any { it.scope == month.toString() })
        assertTrue(db.dao().allExpenses().isEmpty())
    }
    @Test fun missedMonthsReconcileAsPlansWithoutFinancialTransactions() = runBlocking {
        val prior = month.minusMonths(3)
        finance.dao.putRecurring(rule().copy(startDate = prior.atDay(1).toString(), nextDate = prior.atDay(1).toString()))
        finance.ensureMonth(prior); finance.reconcile()
        assertEquals((0L..3L).map { prior.plusMonths(it).toString() }, db.plannerDao().allMonths().map { it.month })
        assertTrue(db.dao().allExpenses().isEmpty())
    }
    @Test fun journeyCrudAndDuplicateHaveNoExpenseOrFuelSideEffects() = runBlocking {
        val repository = JourneyRepository(db)
        val first = savedJourney("first", JourneyPlan("KL", "Penang", 350.0))
        repository.save(first); repository.save(first.copy(payload = backupJson.encodeToString(first.plan().copy(parkingCents = 100))))
        repository.save(savedJourney("duplicate", first.plan().copy(petrolPrice = "3.00")))
        assertEquals(2, db.journeyDao().allJourneys().size)
        assertTrue(db.dao().allExpenses().isEmpty()); assertTrue(db.dao().allRefuels().isEmpty())
        repository.delete("first"); assertEquals("duplicate", db.journeyDao().allJourneys().single().id)
    }
    private fun receipt() = parseReceipt("McDonald's\n${LocalDate.now()}\nGRAND TOTAL RM12.34")
    private fun receiptExpense(id: String = "scan") = Expense(id, 1234, "food", date().atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), "McDonald's", payment = "Not specified")
    @Test fun receiptAtomicSaveDeduplicatesConcurrentScansAndUndoRemovesOnlyItsExpense() = runBlocking {
        ledger.saveSettings(AppSettings(receiptSaveMode = "Smart"))
        val receipt = receipt(); val fingerprint = receiptFingerprint(receipt.rawText)
        val saved = receiptExpense().copy(timestamp = LocalDate.now().atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())
        val outcomes = coroutineScope { List(3) { async(Dispatchers.IO) { runCatching { ledger.saveReceiptExpense(saved, receipt, fingerprint, true, true) } } }.awaitAll() }
        assertEquals(1, outcomes.count { it.isSuccess }); assertEquals(1, db.dao().allExpenses().size)
        assertEquals("food", db.receiptAutomationDao().allMerchants().single().categoryId)
        assertEquals(saved.cents, financeBudgetSpending(FinanceBudget("limit", month.toString(), 5000), db.dao().allExpenses()))
        ledger.undoReceiptExpense(saved)
        assertTrue(db.dao().allExpenses().isEmpty()); assertTrue(db.receiptAutomationDao().allScans().isEmpty())
    }
    @Test fun automaticSavingRequiresEnabledModeAndUnchangedExtractedFields() = runBlocking {
        val receipt = receipt(); val value = receiptExpense().copy(timestamp = LocalDate.now().atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())
        val fingerprint = receiptFingerprint(receipt.rawText)
        assertTrue(runCatching { ledger.saveReceiptExpense(value, receipt, fingerprint, false, true) }.isFailure)
        ledger.saveSettings(AppSettings(receiptSaveMode = "Smart"))
        assertTrue(runCatching { ledger.saveReceiptExpense(value.copy(cents = 9999), receipt, fingerprint, false, true) }.isFailure)
        assertTrue(runCatching { ledger.saveReceiptExpense(value.copy(description = "Other merchant"), receipt, fingerprint, false, true) }.isFailure)
        assertTrue(db.dao().allExpenses().isEmpty()); assertTrue(db.receiptAutomationDao().allScans().isEmpty())
    }
    @Test fun incompleteReceiptCannotAutoSaveAndReviewCanCorrectMissingFields() = runBlocking {
        val receipt = parseReceipt("Watsons\nTOTAL RM12.34")
        val value = receiptExpense()
        assertTrue(runCatching { ledger.saveReceiptExpense(value, receipt, receiptFingerprint(receipt.rawText), false, true) }.isFailure)
        assertTrue(db.dao().allExpenses().isEmpty()); assertTrue(db.receiptAutomationDao().allScans().isEmpty())
        ledger.saveReceiptExpense(value, receipt, receiptFingerprint(receipt.rawText), false, false)
        assertEquals(1, db.dao().allExpenses().size)
    }
    @Test fun petrolReceiptLinksExistingRefuelInsteadOfDuplicatingFuel() = runBlocking {
        val stamp = LocalDate.now().atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        ledger.saveRefuel(Refuel("fill", stamp, 1000.0, 10.0, 1234, false, "Petronas"))
        val receipt = parseReceipt("PETRONAS\n${LocalDate.now()}\nTOTAL RM12.34")
        assertTrue(possibleReceiptDuplicate(receipt, db.dao().allExpenses()))
        assertTrue(runCatching { ledger.saveReceiptExpense(receiptExpense(), receipt, receiptFingerprint(receipt.rawText), false, false) }.isFailure)
        ledger.attachScannedReceipt(db.dao().allExpenses().single().id, null, receiptFingerprint(receipt.rawText))
        assertEquals(1, db.dao().allExpenses().size); assertEquals(1, db.dao().allRefuels().size)
    }
    @Test fun undoRejectsChangedTransactionAndNormalDeletionRetainsDuplicateTombstone() = runBlocking {
        val receipt = receipt(); val value = receiptExpense(); val fingerprint = receiptFingerprint(receipt.rawText)
        ledger.saveReceiptExpense(value, receipt, fingerprint, false, false)
        ledger.saveExpense(value.copy(cents = 1500))
        assertTrue(runCatching { ledger.undoReceiptExpense(value) }.isFailure)
        ledger.deleteExpense(value.id); assertNotNull(db.receiptAutomationDao().scan(fingerprint))
        assertTrue(runCatching { ledger.saveReceiptExpense(value.copy(id = "second"), receipt, fingerprint, false, false) }.isFailure)
    }
    @Test fun allPlannerJourneyAndReceiptMetadataSurviveBackupRoundTrip() = runBlocking {
        finance.saveRecurring(rule()); finance.saveRecurring(rule("salary", 300000, "Income"))
        finance.saveAllocation(allocationPlan("default", defaultAllocations))
        JourneyRepository(db).save(savedJourney("route", JourneyPlan("KL", "Ipoh", 200.0)))
        val receipt = receipt(); ledger.saveReceiptExpense(receiptExpense(), receipt, receiptFingerprint(receipt.rawText), true, false)
        val snapshot = ledger.backup(); snapshot.validate()
        val decoded = backupJson.decodeFromString<Backup>(backupJson.encodeToString(snapshot))
        ledger.restore(decoded)
        assertEquals(snapshot.copy(createdAt = 0), ledger.backup().copy(createdAt = 0))
    }
}
