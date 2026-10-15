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
import kotlin.math.roundToInt
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription

@Composable
fun MonthlyPlanner(state: FinanceState, vm: FinanceViewModel, selectedMonth: String? = null,
    selectMonth: ((String) -> Unit)? = null, compact: Boolean = false, openDetails: () -> Unit = {}) {
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
        if (compact) {
            MonthlyAvailableSummary(money)
            ExpandableHelp("Planning details") {
                Text("Planned from expected income less fixed commitments; not a bank balance. Savings allocations are reserves, not transfers.", style = MaterialTheme.typography.bodySmall)
            }
            TextButton(openDetails) { Text("View monthly details") }
        } else {
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
        ExpandableHelp("Allocation details") { Text("Savings is a reserve, not a transfer. Edit allocation defaults or a month override inside Monthly Money Planner.", style = MaterialTheme.typography.bodySmall) }
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
        ExpandableHelp("History and dates") { Text("Saved past plans stay unchanged; future months use active rules. Dates on the 29th–31st use a shorter month's last day.", style = MaterialTheme.typography.bodySmall) }
        }
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
    var encoded by rememberSaveable(scope, stored?.payload) {
        val seed = stored?.categories() ?: fallback
        val flexible = flexibleAllocationId(seed)
        mutableStateOf(backupJson.encodeToString(seed.map { it.copy(remainder = it.id == flexible) }))
    }
    val draft = backupJson.decodeFromString<List<AllocationCategory>>(encoded)
    fun update(value: List<AllocationCategory>) { encoded = backupJson.encodeToString(value) }
    val flexible = flexibleAllocationId(draft)
    val interaction = rememberAppLockInteraction()
    var editingId by rememberSaveable(scope) { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val occurrences = state.months.find { it.month == month.toString() }?.occurrences() ?: plannedOccurrences(state.recurring, month)
    val money = monthlyMoney(month, occurrences, state.income, state.expenses)
    val exact = allocationAmounts(money.plannedAvailable, draft, emptyList(), month)
    val busy by vm.busy.collectAsStateWithLifecycle()
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Choice("Apply to", scope, listOf("default" to "Default allocations", month.toString() to "$month only")) { scope = it; error = null }
        Text("Available for allocation", style = MaterialTheme.typography.labelLarge)
        Text(Money.format(money.plannedAvailable), style = MaterialTheme.typography.headlineMedium)
        if (money.plannedAvailable <= 0) Text("No positive amount to allocate.", color = MaterialTheme.colorScheme.error)
        AllocationDonut(draft)
        Choice("Flexible remainder", flexible, draft.map { it.id to it.name }) { selected -> update(draft.map { it.copy(remainder = it.id == selected) }) }
        Text("Sliders exchange percentages only with the flexible remainder.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        draft.forEach { item ->
            key(item.id) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(item.name, style = MaterialTheme.typography.titleSmall)
                            Text(Money.format(exact.first { it.category.id == item.id }.allocated), style = MaterialTheme.typography.bodySmall)
                        }
                        Text("${percentText(item.basisPoints)}%", style = MaterialTheme.typography.titleMedium)
                    }
                    Slider(item.basisPoints.toFloat(), { if (interaction()) update(moveAllocation(draft, item.id, it.roundToInt())) },
                        enabled = !busy && item.id != flexible, valueRange = 0f..10000f,
                        modifier = Modifier.fillMaxWidth().semantics {
                            contentDescription = "${item.name} allocation"
                            stateDescription = "${percentText(item.basisPoints)} percent" + if (item.id == flexible) ", flexible remainder" else ""
                        })
                    if (item.id == flexible) Text("Automatic remainder", style = MaterialTheme.typography.labelSmall)
                    TextButton({ editingId = item.id }, enabled = !busy) { Text("Edit ${item.name}") }
                }
            }
        }
        OutlinedButton({ if (draft.size < 50) update(draft + AllocationCategory(UUID.randomUUID().toString(), "Custom allocation", 0)) }, enabled = !busy && draft.size < 50, modifier = Modifier.fillMaxWidth()) { Text("Add allocation category") }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button({ runCatching { allocationPlan(scope, draft) }.onSuccess { vm.saveAllocation(it) {} }.onFailure { error = it.message } }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Save allocations · 100%") }
        if (scope != "default" && stored != null) TextButton({ vm.resetAllocation(scope) {} }, enabled = !busy) { Text("Use default allocations") }
        ExpandableHelp("Allocation rules") { Text("Expected income minus fixed commitments is distributed to 100%. No money is allocated when available is zero or negative. Savings is a planned reserve. Overrides change only their selected month.", style = MaterialTheme.typography.bodySmall) }
    }
    draft.find { it.id == editingId }?.let { seed ->
        AllocationCategoryEditor(seed, draft, state.categories, busy, { editingId = null }, { updated ->
            val next = draft.map { if (it.id == updated.id) updated else it }
            runCatching { allocationPlan(scope, next) }.onSuccess { update(next); editingId = null }.onFailure { error = it.message }
        }, if (seed.id == flexible) null else ({ update(removeAllocation(draft, seed.id)); editingId = null }))
    }
}

@Composable
private fun AllocationCategoryEditor(seed: AllocationCategory, all: List<AllocationCategory>, categories: List<Category>, busy: Boolean,
    close: () -> Unit, save: (AllocationCategory) -> Unit, delete: (() -> Unit)?) {
    var name by rememberSaveable(seed.id) { mutableStateOf(seed.name) }
    var savings by rememberSaveable(seed.id) { mutableStateOf(seed.savings) }
    var mapped by rememberSaveable(seed.id) { mutableStateOf(seed.categoryIds) }
    var error by remember { mutableStateOf<String?>(null) }
    EditorShell("Allocation category", busy, error, close, {
        if (name.isBlank()) error = "Enter a name." else save(seed.copy(name = name.trim(), savings = savings, categoryIds = if (savings) emptyList() else mapped))
    }, delete) {
        TextField(name, { name = it }, "Category name", 100)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text("Savings reserve", Modifier.weight(1f)); Switch(savings, { savings = it; if (it) mapped = emptyList() }) }
        if (!savings) displayCategories(categories).forEach { category ->
            val elsewhere = all.any { it.id != seed.id && category.id in it.categoryIds }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(category.id in mapped, { checked -> mapped = if (checked) mapped + category.id else mapped - category.id }, enabled = !elsewhere)
                Text(category.name, Modifier.weight(1f))
            }
        }
        if (seed.remainder) Text("Choose another flexible remainder to remove this category.", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
internal fun MonthlyAvailableSummary(money: MonthlyMoney) {
    Text("Available After Fixed Commitments", style = MaterialTheme.typography.labelLarge)
    Text(Money.format(money.plannedAvailable), style = MaterialTheme.typography.headlineLarge,
        color = if (money.plannedAvailable < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
    ResponsivePair(first = { Stat("Expected Income", Money.format(money.expected), it) },
        second = { Stat("Fixed Expenses", Money.format(money.fixed), it) })
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
        ExpandableHelp("Calculation details") { Text("These are plans, not a bank balance. Allowance uses expected income minus fixed commitments and variable spending. Reserved fixed payments are excluded from variable spending; payments for other months count as unreserved spending here.", style = MaterialTheme.typography.bodySmall) }
    }
}
