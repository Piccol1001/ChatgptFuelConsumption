package my.fuelledger.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import my.fuelledger.FinanceState
import my.fuelledger.FinanceViewModel
import my.fuelledger.data.*
import my.fuelledger.domain.Money
import java.math.BigDecimal
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.UUID

@Composable
fun MonthlyPlanner(state: FinanceState, vm: FinanceViewModel, selectedMonth: String? = null,
    selectMonth: ((String) -> Unit)? = null) {
    val plannerBusy by vm.busy.collectAsStateWithLifecycle()
    val operationError by vm.error.collectAsStateWithLifecycle()
    val calendar = currentCalendar()
    val currentMonth = YearMonth.from(calendar.today).toString()
    var localMonth by rememberSaveable(currentMonth) { mutableStateOf(currentMonth) }
    val month = YearMonth.parse(selectedMonth ?: localMonth)
    val setMonth: (String) -> Unit = { if (selectMonth != null) selectMonth(it) else localMonth = it }
    LaunchedEffect(month, currentMonth) { vm.openMonth(month) }
    val occurrences = state.months.firstOrNull { it.month == month.toString() }?.occurrences()
        ?: plannedOccurrences(state.recurring, month)
    val money = monthlyMoney(month, occurrences, state.income, state.expenses, calendar.zone)
    val categories = (state.allocations.find { it.scope == month.toString() }
        ?: state.allocations.find { it.scope == "default" })?.categories() ?: defaultAllocations
    val allocations = allocationAmounts(money.plannedAvailable, categories, state.expenses, month, calendar.zone, occurrences)
    var payment by remember { mutableStateOf<PlannedOccurrence?>(null) }
    LedgerCard(emphasized = true) {
        Text("Monthly Money Planner", style = MaterialTheme.typography.titleLarge)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton({ setMonth(month.minusMonths(1).toString()) }, enabled = month > YearMonth.of(1970, 1)) { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, "Previous planning month") }
            Text(month.format(DateTimeFormatter.ofPattern("MMMM yyyy")), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            IconButton({ setMonth(month.plusMonths(1).toString()) }, enabled = month < YearMonth.of(2999, 12)) { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, "Next planning month") }
        }
        MonthlyPlanSummary(money)
        Text("Monthly allocations", style = MaterialTheme.typography.titleMedium)
        allocations.forEach {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("${it.category.name} · ${percentText(it.category.basisPoints)}%", style = MaterialTheme.typography.labelLarge)
                Text(if (it.category.savings) "Planned reserve ${Money.format(it.allocated)} · no transfer recorded"
                    else "Allocated ${Money.format(it.allocated)} · Spent ${Money.format(it.spent)} · Remaining ${Money.format(it.remaining)}",
                    style = MaterialTheme.typography.bodySmall)
            }
        }
        val mappedIds = categories.flatMap { it.categoryIds }.toSet()
        val unmapped = variableExpensesForMonth(month, occurrences, state.expenses, calendar.zone).filter { it.categoryId !in mappedIds }.sumOf { it.cents }
        if (unmapped > 0) MoneyLine("Variable spending without allocation", unmapped)
        Text("Savings is a planned reserve. Allocation percentages never create transactions. Configure defaults or a month override in Settings → Financial Planning → Monthly Allocation.", style = MaterialTheme.typography.bodySmall)
        Text("Fixed payments", style = MaterialTheme.typography.titleMedium)
        val fixed = occurrences.filter { it.kind == "Expense" }.sortedBy { it.date }
        if (fixed.isEmpty()) Text("No commitments. Add fixed expenses and expected income under Financial Planning.")
        fixed.forEach { due ->
            val paid = state.expenses.firstOrNull { it.recurringRuleId == due.ruleId && it.recurringDate == due.date }
            HorizontalDivider()
            Text(due.name, style = MaterialTheme.typography.titleSmall)
            Text("${due.date} · ${Money.format(due.cents)} · ${if (paid != null) "Paid ${Money.format(paid.cents)} on ${Instant.ofEpochMilli(paid.timestamp).atZone(calendar.zone).toLocalDate()}" else if (LocalDate.parse(due.date) < calendar.today) "Overdue · unpaid" else "Upcoming · unpaid"}", style = MaterialTheme.typography.bodySmall)
            if (paid == null) OutlinedButton({ payment = due }, modifier = Modifier.fillMaxWidth()) { Text("Record payment / link expense") }
            else Text("Edit the linked transaction in Expenses to update the actual payment.", style = MaterialTheme.typography.bodySmall)
        }
        operationError?.let { Text(it, color = MaterialTheme.colorScheme.error); TextButton(vm::dismissError) { Text("Dismiss planning error") } }
        Text("Past saved plans stay unchanged. Future months are forecasts using active rules. Monthly dates on the 29th–31st use the month's last valid day.", style = MaterialTheme.typography.bodySmall)
    }
    payment?.let { due -> PaymentEditor(due, state, plannerBusy, operationError, { payment = null }) { date, amount, existing -> vm.markPaid(due, date, amount, existing) { payment = null } } }
}

@Composable
private fun MoneyLine(label: String, cents: Long) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(Money.format(cents), style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun PaymentEditor(due: PlannedOccurrence, state: FinanceState, busy: Boolean, operationError: String?, dismiss: () -> Unit,
    save: (LocalDate, Long, String?) -> Unit) {
    var amount by rememberSaveable { mutableStateOf(Money.decimal(due.cents)) }
    var date by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var existing by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    EditorShell("Pay ${due.name}", busy, error ?: operationError, dismiss, {
        runCatching { LocalDate.parse(date) to Money.parse(amount) }
            .onSuccess { save(it.first, it.second, existing.ifBlank { null }) }.onFailure { error = it.message }
    }, null) {
        Choice("Payment record", existing, listOf("" to "Create one actual expense") + state.expenses.filter { it.recurringRuleId == null && it.fuelId == null }.take(200).map {
            it.id to "${it.description.ifBlank { "Expense" }} · ${Money.format(it.cents)} · ${Instant.ofEpochMilli(it.timestamp).atZone(ZoneId.systemDefault()).toLocalDate()}"
        }) { existing = it }
        if (existing.isBlank()) {
            NumericField(amount, { amount = it }, "Actual paid amount (RM)")
            DateButton(LocalDate.parse(date), "Actual payment date", { date = it.toString() }, Modifier.fillMaxWidth())
        }
        Text("Linking preserves the selected transaction's amount, date and receipt. It does not create another expense.")
    }
}

fun percentText(points: Int): String = BigDecimal(points).movePointLeft(2).stripTrailingZeros().toPlainString()

@Composable
fun MonthlyAllocationSettings(state: FinanceState, vm: FinanceViewModel, month: YearMonth) {
    var scope by rememberSaveable(month.toString()) { mutableStateOf("default") }
    val stored = state.allocations.find { it.scope == scope }
    val fallback = state.allocations.find { it.scope == "default" }?.categories() ?: defaultAllocations
    var encoded by rememberSaveable(scope, stored?.payload) { mutableStateOf(backupJson.encodeToString(stored?.categories() ?: fallback)) }
    val draft = backupJson.decodeFromString<List<AllocationCategory>>(encoded)
    fun update(value: List<AllocationCategory>) { encoded = backupJson.encodeToString(value) }
    var error by remember { mutableStateOf<String?>(null) }
    val occurrences = state.months.find { it.month == month.toString() }?.occurrences() ?: plannedOccurrences(state.recurring, month)
    val money = monthlyMoney(month, occurrences, state.income, state.expenses)
    val exactAmounts = runCatching { allocationAmounts(money.plannedAvailable, draft, emptyList(), month) }.getOrNull()
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Monthly Allocation", style = MaterialTheme.typography.titleLarge)
        Choice("Apply to", scope, listOf("default" to "Default for each new month", month.toString() to "Override $month only")) { scope = it; error = null }
        Text("Available for allocation · $month", style = MaterialTheme.typography.labelLarge)
        Text(Money.format(money.plannedAvailable), style = MaterialTheme.typography.headlineMedium)
        Text("Expected income − fixed commitments. Zero or negative available amounts allocate RM 0.00. Savings is a reserve, never an expense. Defaults apply to new months; month overrides stay independent.", style = MaterialTheme.typography.bodySmall)
        draft.forEach { item ->
            key(item.id) {
                LedgerCard {
                    TextField(item.name, { name -> update(draft.map { if (it.id == item.id) it.copy(name = name) else it }) }, "Allocation name", 100)
                    var percent by rememberSaveable(scope, item.id) { mutableStateOf(percentText(item.basisPoints)) }
                    NumericField(percent, { value ->
                        percent = value
                        val points = runCatching { value.toBigDecimal().movePointRight(2).intValueExact() }.getOrDefault(-1)
                        update(draft.map { if (it.id == item.id) it.copy(basisPoints = points) else it })
                    }, "Percentage (%)")
                    Text("Planned ${Money.format(exactAmounts?.firstOrNull { it.category.id == item.id }?.allocated ?: if (item.basisPoints in 0..10000) BigDecimal(maxOf(0, money.plannedAvailable)).multiply(BigDecimal(item.basisPoints)).divideToIntegralValue(BigDecimal(10000)).longValueExact() else 0)}")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Savings reserve", Modifier.weight(1f))
                        Switch(item.savings, { reserve -> update(draft.map { if (it.id == item.id) it.copy(savings = reserve, categoryIds = if (reserve) emptyList() else it.categoryIds) else it }) })
                    }
                    if (!item.savings) {
                        Text("Track spending from these categories:", style = MaterialTheme.typography.bodySmall)
                        state.categories.forEach { category ->
                            val checked = category.id in item.categoryIds
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked, { selected -> update(draft.map { if (it.id == item.id) it.copy(categoryIds = if (selected) it.categoryIds + category.id else it.categoryIds - category.id) else it }) })
                                Text(category.name, Modifier.weight(1f))
                            }
                        }
                    }
                    TextButton({ update(draft.filterNot { it.id == item.id }) }) { Text("Remove allocation") }
                }
            }
        }
        OutlinedButton({ if (draft.size < 50) update(draft + AllocationCategory(UUID.randomUUID().toString(), "Custom allocation", 0)) }, modifier = Modifier.fillMaxWidth()) { Text("Add allocation category") }
        Text("Total: ${percentText(draft.sumOf { it.basisPoints })}% · required 100%", style = MaterialTheme.typography.titleMedium)
        val valid = runCatching { allocationPlan(scope, draft) }
        val busy by vm.busy.collectAsState()
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (valid.isFailure) Text(valid.exceptionOrNull()?.message.orEmpty(), color = MaterialTheme.colorScheme.error)
        Button({ valid.onSuccess { vm.saveAllocation(it) { error = null } }.onFailure { error = it.message } }, enabled = !busy && valid.isSuccess, modifier = Modifier.fillMaxWidth()) { Text("Save allocation percentages") }
        if (scope != "default" && stored != null) TextButton({ vm.resetAllocation(scope) {} }, enabled = !busy) { Text("Remove month override · use default") }
    }
}

@Composable
internal fun MonthlyPlanSummary(money: MonthlyMoney) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        MoneyLine("Expected income", money.expected)
        MoneyLine("Fixed commitments", money.fixed)
        Text("Planned available", style = MaterialTheme.typography.labelLarge)
        Text(Money.format(money.plannedAvailable), style = MaterialTheme.typography.headlineLarge,
            color = if (money.plannedAvailable < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
        if (money.plannedAvailable < 0) Text("Fixed commitments exceed expected income. Allocations are RM 0.00 until the shortfall is covered.", color = MaterialTheme.colorScheme.error)
        HorizontalDivider()
        MoneyLine("Actual income received", money.actualIncome)
        MoneyLine("Actual expenditure", money.actualExpenses)
        MoneyLine("Actual monthly net cash flow", money.actualNet)
        MoneyLine("Variable / unreserved spending", money.variableExpenses)
        MoneyLine("Remaining spending allowance", money.remainingAllowance)
        LinearProgressIndicator(progress = { if (money.plannedAvailable <= 0) 0f else (money.variableExpenses.toDouble() / money.plannedAvailable).coerceIn(0.0, 1.0).toFloat() }, modifier = Modifier.fillMaxWidth())
        Text("Allowance uses the original expected income minus fixed commitments and recorded variable spending. Paid fixed bills reserved in this month stay in actual expenditure and are not deducted again. Payments for other months count as unreserved spending here. These are plans, not a bank balance.", style = MaterialTheme.typography.bodySmall)
    }
}
