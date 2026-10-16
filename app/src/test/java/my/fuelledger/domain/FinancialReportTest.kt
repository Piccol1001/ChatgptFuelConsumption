package my.fuelledger.domain

import my.fuelledger.LedgerState
import my.fuelledger.FinanceState
import my.fuelledger.data.*
import org.junit.Test
import org.junit.Assert.*
import java.time.*

class FinancialReportTest {
    @Test fun reportSeparatesIncomeRefundsFixedAndForecastAtLocalCalendarBoundaries() {
        val zone = ZoneId.of("Asia/Kuala_Lumpur"); val day = LocalDate.of(2026, 10, 1)
        val time = day.atStartOfDay(zone).toInstant().toEpochMilli()
        val expenses = listOf(Expense("fixed", 10000, "bills", time, recurringRuleId = "r", recurringDate = day.toString()), Expense("food", 2000, "food", time), Expense("refund", -500, "food", time, kind = "Refund", refundOf = "food"), Expense("prior", 999, "food", time - 1))
        val state = LedgerState(defaultCategories, expenses)
        val finance = FinanceState(income = listOf(FinanceIncome("salary", 300000, time, "Salary")))
        val report = financialReport(state, finance, day, day.withDayOfMonth(31), zone)
        assertEquals(11500L, report.expenses); assertEquals(10000L, report.fixedPaid); assertEquals(1500L, report.variable); assertEquals(288500L, report.net)
        assertTrue(report.csvText().contains("Refund")); assertFalse(report.csvText().contains("prior")); assertEquals(1500L, report.categoryTotals["food"])
    }
    @Test fun insufficientHistoryCreatesNoFictionalInsights() {
        assertTrue(financialInsights(LedgerState(categories = defaultCategories), FinanceState(), YearMonth.of(2026, 10), LocalDate.of(2026, 10, 16), ZoneOffset.UTC).isEmpty())
    }
}
