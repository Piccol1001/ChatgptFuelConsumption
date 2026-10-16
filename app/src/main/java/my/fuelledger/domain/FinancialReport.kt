package my.fuelledger.domain

import my.fuelledger.FinanceState
import my.fuelledger.LedgerState
import my.fuelledger.data.*
import java.time.*

data class FinancialReport(val start: LocalDate, val end: LocalDate, val income: Long, val expenses: Long,
    val fixedPaid: Long, val variable: Long, val fuel: Long, val categoryTotals: Map<String, Long>, val rows: List<List<String>>) {
    val net get() = income - expenses
    fun csvText() = csv(rows)
    fun lines() = listOf("Financial report", "$start to $end", "Actual income: ${Money.format(income)}", "Net expenditure: ${Money.format(expenses)}",
        "Actual net cash flow: ${Money.format(net)}", "Paid fixed expenses: ${Money.format(fixedPaid)}", "Variable expenses: ${Money.format(variable)}",
        "Recorded fuel/transport: ${Money.format(fuel)}", "Net cash flow is not a bank balance.") + rows.drop(1).map { it.joinToString(" | ") }
}
fun financialReport(state: LedgerState, finance: FinanceState, start: LocalDate, end: LocalDate, zone: ZoneId): FinancialReport {
    require(end >= start) { "Choose a valid date range." }
    fun date(time: Long) = Instant.ofEpochMilli(time).atZone(zone).toLocalDate()
    val expenses = state.expenses.filter { date(it.timestamp) in start..end }
    val income = finance.income.filter { date(it.timestamp) in start..end }
    val fixed = expenses.filter { it.recurringRuleId != null }.sumOf { it.cents }
    val categories = expenses.groupBy { it.categoryId }.mapValues { it.value.sumOf { e -> e.cents } }
    val names = state.categories.associate { it.id to it.name }
    val budgets = finance.budgets.filter { YearMonth.parse(it.month).atDay(1) <= end && YearMonth.parse(it.month).atEndOfMonth() >= start }
    val rows = listOf(listOf("Type", "Date / month", "Name / category", "MYR", "Notes / actual")) +
        income.map { listOf("Income", date(it.timestamp).toString(), it.source, Money.decimal(it.cents), it.notes) } +
        expenses.map { listOf(it.kind, date(it.timestamp).toString(), "${it.merchant.ifBlank { it.description }} · ${names[it.categoryId].orEmpty()}", Money.decimal(it.cents), it.payment) } +
        categories.map { (id, cents) -> listOf("Category net spending", "$start to $end", names[id].orEmpty(), Money.decimal(cents), "Actual") } +
        budgets.map { listOf("Monthly budget", it.month, names[it.categoryId] ?: "Overall", Money.decimal(it.cents), "Actual full month: ${Money.decimal(financeBudgetSpending(it, state.expenses, zone))}; budget applies to full calendar month") }
    return FinancialReport(start, end, income.sumOf { it.cents }, expenses.sumOf { it.cents }, fixed,
        expenses.sumOf { it.cents } - fixed, expenses.filter { it.categoryId == FUEL_CATEGORY }.sumOf { it.cents }, categories, rows)
}
fun financialInsights(state: LedgerState, finance: FinanceState, month: YearMonth, today: LocalDate, zone: ZoneId): List<String> = buildList {
    val spending = Spending(state.expenses.map { it.point() }, zone)
    val current = spending.categories(month.atDay(1), month.atEndOfMonth())
    val priorMonth = month.minusMonths(1)
    val prior = spending.categories(priorMonth.atDay(1), priorMonth.atEndOfMonth())
    for (category in state.categories) {
        val now = current[category.id] ?: 0; val before = prior[category.id] ?: 0
        if (before > 0 && now > before) add("${category.name} spending increased ${String.format(Malaysia, "%.0f", (now - before) * 100.0 / before)}% vs previous month${if (month == YearMonth.from(today)) " (current month to date)" else ""}.")
    }
    finance.budgets.filter { it.month == month.toString() }.forEach {
        val spent = financeBudgetSpending(it, state.expenses, zone)
        if (spent >= it.cents * .9) add("${state.categories.find { c -> c.id == it.categoryId }?.name ?: "Overall"} budget usage reached ${spent * 100 / it.cents}%.")
    }
    val plan = finance.months.find { it.month == month.toString() }?.occurrences() ?: plannedOccurrences(finance.recurring, month)
    val upcoming = plan.filter { it.kind == "Expense" && LocalDate.parse(it.date) >= today && state.expenses.none { e -> e.recurringRuleId == it.ruleId && e.recurringDate == it.date } }.sumOf { it.cents }
    if (upcoming > 0) add("Upcoming fixed commitments total ${Money.format(upcoming)}.")
    consumptionChange(state.refuels.map { it.point() })?.let { add("Recent valid-interval fuel consumption ${if (it.percent >= 0) "increased" else "decreased"} ${String.format(Malaysia, "%.1f", kotlin.math.abs(it.percent))}%.") }
}.take(8)
