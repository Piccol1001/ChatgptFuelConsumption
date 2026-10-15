package my.fuelledger.data

import my.fuelledger.domain.*
import my.fuelledger.receipts.*
import org.junit.Assert.*
import org.junit.Test
import java.time.*

class PlanningCalculationsTest {
    private val month = YearMonth.of(2026, 2)
    private val zone = ZoneId.of("Asia/Kuala_Lumpur")
    private fun rule(id: String, cents: Long, day: Int = 1, kind: String = "Expense") = FinanceRecurring(id, id, cents,
        if (kind == "Expense") "bills" else "", "Monthly", "2026-01-${day.toString().padStart(2, '0')}", "2026-01-${day.toString().padStart(2, '0')}", kind = kind, manualPayment = true)
    private fun stamp(date: String) = LocalDate.parse(date).atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
    @Test fun requestedMonthlyExampleUsesExpectedRatherThanReceivedIncome() {
        val plan = plannedOccurrences(listOf(rule("salary", 300000, kind = "Income"), rule("rent", 95000), rule("wifi", 12000, 15), rule("mobile", 6000, 20)), month)
        val money = monthlyMoney(month, plan, emptyList(), emptyList(), zone)
        assertEquals(300000L, money.expected); assertEquals(113000L, money.fixed)
        assertEquals(187000L, money.plannedAvailable); assertEquals(0L, money.actualNet)
    }
    @Test fun fixedPaidExpenseOnlyDeductsOnceButStaysInActualExpenditure() {
        val plan = plannedOccurrences(listOf(rule("salary", 300000, kind = "Income"), rule("rent", 95000)), month)
        val expenses = listOf(Expense("rent-paid", 96000, "bills", stamp("2026-02-01"), recurringRuleId = "rent", recurringDate = "2026-02-01"), Expense("lunch", 10000, "food", stamp("2026-02-02")))
        val money = monthlyMoney(month, plan, listOf(FinanceIncome("actual", 250000, stamp("2026-02-03"))), expenses, zone)
        assertEquals(205000L, money.plannedAvailable); assertEquals(195000L, money.remainingAllowance)
        assertEquals(106000L, money.actualExpenses); assertEquals(144000L, money.actualNet)
    }
    @Test fun negativeAvailableRemainsVisibleAndAllocatesZero() {
        val money = monthlyMoney(month, plannedOccurrences(listOf(rule("rent", 95000)), month), emptyList(), emptyList(), zone)
        assertEquals(-95000L, money.plannedAvailable)
        assertTrue(allocationAmounts(money.plannedAvailable, defaultAllocations, emptyList(), month).all { it.allocated == 0L })
        assertTrue(allocationAmounts(0, defaultAllocations, emptyList(), month).all { it.allocated == 0L })
    }
    @Test fun allocationRoundingPreservesEverySenDeterministically() {
        for (cents in 0L..111L) assertEquals(cents, allocationAmounts(cents, defaultAllocations, emptyList(), month).sumOf { it.allocated })
        val parts = listOf(AllocationCategory("a", "A", 3333), AllocationCategory("b", "B", 3333), AllocationCategory("c", "C", 3334))
        assertEquals(listOf(0L, 0L, 1L), allocationAmounts(1, parts, emptyList(), month).map { it.allocated })
        assertEquals(Long.MAX_VALUE, allocationAmounts(Long.MAX_VALUE, parts, emptyList(), month).sumOf { it.allocated })
    }
    @Test fun invalidPercentagesAndOverlappingMappingsAreRejected() {
        val cases = listOf(emptyList(), listOf(AllocationCategory("x", "X", 9999)), listOf(AllocationCategory("x", "X", 10001)),
            listOf(AllocationCategory("x", "X", -1), AllocationCategory("y", "Y", 10001)),
            listOf(AllocationCategory("x", "X", 5000, listOf("food")), AllocationCategory("y", "Y", 5000, listOf("food"))),
            listOf(AllocationCategory("x", "X", 10000, listOf("food"), savings = true)))
        cases.forEach { assertTrue(runCatching { allocationPlan("default", it) }.isFailure) }
    }
    @Test fun prepaidCommitmentForAnotherMonthCountsAsUnreservedSpendingHere() {
        val due = PlannedOccurrence("rent", "2026-03-01", "Rent", 95000, "bills", "Expense")
        val expense = Expense("early", 95000, "bills", stamp("2026-02-28"), recurringRuleId = "rent", recurringDate = due.date)
        assertEquals(95000L, monthlyMoney(month, emptyList(), emptyList(), listOf(expense), zone).variableExpenses)
        assertEquals(95000L, allocationAmounts(100000, defaultAllocations, listOf(expense), month, zone).sumOf { it.spent })
    }
    @Test fun categorySpendingIgnoresFixedPaymentsAndSavingsNeverBecomesExpense() {
        val expenses = listOf(Expense("variable", 1000, "food", stamp("2026-02-01")), Expense("fixed", 2000, "food", stamp("2026-02-02"), recurringRuleId = "rule", recurringDate = "2026-02-01"))
        val amounts = allocationAmounts(10000, defaultAllocations, expenses, month, zone, listOf(PlannedOccurrence("rule", "2026-02-01", "Fixed", 2000, "food", "Expense")))
        assertEquals(1000L, amounts.sumOf { it.spent }); assertEquals(0L, amounts.first { it.category.savings }.spent)
        assertEquals(2000L, amounts.first { it.category.id == "food" }.remaining)
    }
    @Test fun monthEndDatesRecoverAndStartEndPauseAreRespected() {
        val value = rule("rent", 1000, 31)
        assertEquals("2026-02-28", plannedOccurrences(listOf(value), month).single().date)
        assertEquals("2026-03-31", plannedOccurrences(listOf(value), month.plusMonths(1)).single().date)
        assertTrue(plannedOccurrences(listOf(value.copy(enabled = false)), month).isEmpty())
        assertTrue(plannedOccurrences(listOf(value.copy(endDate = "2026-02-15")), month).isEmpty())
        assertTrue(plannedOccurrences(listOf(value.copy(startDate = "2026-03-31", nextDate = "2026-03-31")), month).isEmpty())
    }
    @Test fun weeklyAndDailyPlansDoNotUsePostingCursor() {
        val weekly = rule("weekly", 1000).copy(frequency = "Weekly", nextDate = "2027-01-07")
        assertEquals(4, plannedOccurrences(listOf(weekly), month).size)
        assertEquals(28, plannedOccurrences(listOf(weekly.copy(frequency = "Daily")), month).size)
    }
    @Test fun localMonthBoundariesAndDstUseCalendarDates() {
        val expense = Expense("edge", 1234, "food", Instant.parse("2026-01-31T18:00:00Z").toEpochMilli())
        assertEquals(1234L, monthlyMoney(month, emptyList(), emptyList(), listOf(expense), zone).variableExpenses)
        assertEquals(0L, monthlyMoney(month, emptyList(), emptyList(), listOf(expense), ZoneOffset.UTC).variableExpenses)
    }
    @Test fun journeyReusesFuelEstimateAndAddsExactFees() {
        val plan = JourneyPlan("KL", "Penang", 150.0, outboundTolls = listOf(JourneyFee("Highway", 1200)), parkingCents = 500, otherFees = listOf(JourneyFee("Ferry", 100)))
        val cost = journeyCost(plan)
        assertEquals(2153L, cost.fuelCents); assertEquals(3953L, cost.totalCents)
        assertEquals(estimateTrip(150.0, 7.0, "2.05".toBigDecimal()).costCents, cost.fuelCents)
    }
    @Test fun roundTripUsesSeparateDistanceAndNeverDoublesTolls() {
        val plan = JourneyPlan("KL", "Penang", 150.0, true, 200.0, outboundTolls = listOf(JourneyFee("Out", 1200)), returnTolls = listOf(JourneyFee("Return", 900)))
        val cost = journeyCost(plan)
        assertEquals(350.0, cost.distanceKm, 0.0); assertEquals(2100L, cost.tollCents)
        assertEquals(1200L, journeyCost(plan.copy(roundTrip = false)).tollCents)
    }
    @Test fun optionalSharingOnlySplitsSelectedCosts() {
        val plan = JourneyPlan("A", "B", 150.0, outboundTolls = listOf(JourneyFee("Toll", 1000)), parkingCents = 500,
            people = 3, shareFuel = false, shareParking = false)
        assertEquals(1000L, journeyCost(plan).sharedCents); assertEquals(333L, journeyCost(plan).perPersonCents)
    }
    @Test fun invalidJourneyInputsAreRejected() {
        val plan = JourneyPlan("A", "B", 150.0)
        listOf(plan.copy(outboundKm = Double.NaN), plan.copy(outboundKm = -1.0), plan.copy(returnKm = Double.POSITIVE_INFINITY),
            plan.copy(people = 0), plan.copy(people = 1001), plan.copy(petrolPrice = "0"), plan.copy(parkingCents = -1),
            plan.copy(outboundTolls = listOf(JourneyFee("", 1))), plan.copy(otherFees = listOf(JourneyFee("Other", MAX_CENTS))))
            .forEach { assertTrue(runCatching { journeyCost(it) }.isFailure) }
        assertTrue(runCatching { savedJourney("id", plan.copy(origin = "")) }.isFailure)
    }
    @Test fun malaysianReceiptKeywordsRejectSubtotalTenderTaxAndChange() {
        for (label in listOf("TOTAL", "GRAND TOTAL", "AMOUNT PAID", "NET TOTAL", "JUMLAH", "JUMLAH BAYARAN")) {
            val receipt = parseReceipt("McDonald's\n15/10/2026\nSUBTOTAL 18.00\nSST 1.08\n$label RM19.08\nCASH 50.00\nCHANGE 30.92")
            assertEquals(label, "19.08", receipt.amount); assertEquals("food", receipt.suggestedCategoryId)
            assertTrue(receipt.strictTotal); assertFalse(receipt.ambiguousTotals)
        }
    }
    @Test fun strictAutoSaveAllowsClearReceiptAndRejectsAllAmbiguities() {
        val receipt = parseReceipt("McDonald's\n15/10/2026\nTOTAL RM19.08")
        val today = LocalDate.of(2026, 10, 15)
        assertTrue(receiptAutoSaveIssues(receipt, "food", false, today).isEmpty())
        listOf(receipt.copy(station = null), receipt.copy(date = null), receipt.copy(date = "2026-10-16"), receipt.copy(amount = "-1"),
            receipt.copy(ambiguousTotals = true), receipt.copy(ambiguousDates = true), receipt.copy(currency = "OTHER"),
            receipt.copy(strictTotal = false), receipt.copy(fuelType = "RON95"))
            .forEach { assertTrue(receiptAutoSaveIssues(it, "food", false, today).isNotEmpty()) }
        assertTrue(receiptAutoSaveIssues(receipt, "food", true, today).isNotEmpty())
        assertTrue(receiptAutoSaveIssues(receipt, null, false, today).isNotEmpty())
    }
    @Test fun merchantRulesCoverRequestedExamples() {
        mapOf("McDonald's" to "food", "Lotus's" to "groceries", "Petronas" to FUEL_CATEGORY, "TGV Cinemas" to "entertainment", "Watsons" to "shopping").forEach { (merchant, category) ->
            assertEquals(category, parseReceipt("$merchant\nTOTAL RM12.00").suggestedCategoryId)
        }
        assertEquals(merchantKey("McDonald's"), merchantKey("MCDONALDS"))
        assertEquals(receiptFingerprint("TOTAL RM 12.00"), receiptFingerprint("total rm12.00"))
    }
    @Test fun conflictingDatesTotalsAndForeignCurrencyRequireReview() {
        val receipt = parseReceipt("Watsons\nDATE 15/10/2026\nDATE 14/10/2026\nTOTAL 12.00\nTOTAL 13.00\nUSD")
        assertTrue(receipt.ambiguousTotals); assertTrue(receipt.ambiguousDates); assertEquals("OTHER", receipt.currency)
        assertNull(receipt.amount); assertNull(receipt.date)
    }
}
