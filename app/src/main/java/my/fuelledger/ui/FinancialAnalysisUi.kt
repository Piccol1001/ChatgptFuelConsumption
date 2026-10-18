package my.fuelledger.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import my.fuelledger.FinanceState
import my.fuelledger.LedgerState
import my.fuelledger.data.*
import my.fuelledger.domain.*
import java.time.*

@Composable fun FinancialAnalysisDetails(state: LedgerState, finance: FinanceState, month: YearMonth,
    start: LocalDate, end: LocalDate, today: LocalDate, zone: ZoneId) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
    val report = financialReport(state, finance, start, end, zone)
    LedgerCard {
        Text("Actual financial activity", style = MaterialTheme.typography.titleMedium)
        Text("Income ${Money.format(report.income)} · net expenditure ${Money.format(report.expenses)}")
        Text("Paid fixed ${Money.format(report.fixedPaid)} · variable ${Money.format(report.variable)}")
        Text("Net cash flow ${Money.format(report.net)}", style = MaterialTheme.typography.titleMedium)
    }
    SpendingBars("Income vs Expenses", listOf("Actual income" to report.income, "Net expenses" to report.expenses))
    val months = generateSequence(YearMonth.from(start)) { it.plusMonths(1) }.takeWhile { it <= YearMonth.from(end) }.toList().takeLast(12)
    SpendingBars("Income trend · actual", months.map { m -> m.toString() to finance.income.filter {
        val date = Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalDate(); date in maxOf(start, m.atDay(1))..minOf(end, m.atEndOfMonth())
    }.sumOf { it.cents } })
    val budgets = finance.budgets.filter { YearMonth.parse(it.month) in YearMonth.from(start)..YearMonth.from(end) }
    LedgerCard {
        Text("Budget vs actual", style = MaterialTheme.typography.titleMedium)
        if (budgets.isEmpty()) Text("No budgets configured for this period", style = MaterialTheme.typography.bodySmall)
        budgets.forEach { Text("${it.month} · ${state.categories.find { c -> c.id == it.categoryId }?.name ?: "Overall"}: ${Money.format(financeBudgetSpending(it, state.expenses, zone))} / ${Money.format(it.cents)}", style = MaterialTheme.typography.bodySmall) }
        if (budgets.isNotEmpty()) Text("Budgets apply to full calendar months.", style = MaterialTheme.typography.bodySmall)
    }
    val forecastMonth = maxOf(YearMonth.from(today), month)
    val forecast = plannedOccurrences(finance.recurring, forecastMonth)
    LedgerCard {
        Text("Forecast · $forecastMonth · planned", style = MaterialTheme.typography.titleMedium)
        Text("Expected income ${Money.format(forecast.filter { it.kind == "Income" }.sumOf { it.cents })}")
        Text("Scheduled commitments ${Money.format(forecast.filter { it.kind == "Expense" }.sumOf { it.cents })}")
        Text("Planned available ${Money.format(monthlyMoney(forecastMonth, forecast, emptyList(), emptyList()).plannedAvailable)}")
        Text("Plan only; future variable spending is unknown.", style = MaterialTheme.typography.bodySmall)
    }
    val insights = financialInsights(state, finance, month, today, zone)
    LedgerCard {
        Text("Local insights", style = MaterialTheme.typography.titleMedium)
        if (insights.isEmpty()) Text("More recorded history is needed for comparisons.", style = MaterialTheme.typography.bodySmall)
        insights.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
    }
}
}
