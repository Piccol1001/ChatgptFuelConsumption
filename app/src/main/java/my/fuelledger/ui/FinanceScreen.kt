package my.fuelledger.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import my.fuelledger.FinanceViewModel
import my.fuelledger.data.*
import my.fuelledger.domain.Money
import my.fuelledger.domain.Spending
import java.time.*
import java.util.UUID

@Composable
fun FinanceScreen(vm: FinanceViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val operationError by vm.error.collectAsStateWithLifecycle()
    var section by rememberSaveable { mutableStateOf("Income") }
    var monthText by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    val month = YearMonth.parse(monthText)
    var editor by rememberSaveable { mutableStateOf<String?>(null) }
    var editorId by rememberSaveable { mutableStateOf(UUID.randomUUID().toString()) }
    var incomeSeed by remember { mutableStateOf<FinanceIncome?>(null) }
    var budgetSeed by remember { mutableStateOf<FinanceBudget?>(null) }
    var recurringSeed by remember { mutableStateOf<FinanceRecurring?>(null) }
    var deleting by remember { mutableStateOf<Pair<String, String>?>(null) }
    val close = { editor = null }
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { ScreenHeading("Income & budgets", "Your records, calculated locally. No bank connection is needed.", Icons.Outlined.AccountBalanceWallet) }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { monthText = month.minusMonths(1).toString() }) { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, "Previous finance month") }
                Text(month.toString(), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = { monthText = month.plusMonths(1).toString() }) { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, "Next finance month") }
            }
        }
        item {
            LedgerCard {
                Text("Recorded income · $month", style = MaterialTheme.typography.labelLarge)
                Text(Money.format(financeMonthlyIncome(state.income, month)), style = MaterialTheme.typography.headlineMedium)
                Text("Actual spending: ${Money.format(Spending(state.expenses.map { it.point() }).month(month))}")
                Text("These totals reflect your saved transactions. They do not represent a bank balance.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Income", "Budgets", "Recurring").forEach { title ->
                    FilterChip(selected = section == title, onClick = { section = title }, label = { Text(title) }, modifier = Modifier.heightIn(min = 48.dp))
                }
            }
        }
        item {
            Button(onClick = {
                editorId = UUID.randomUUID().toString()
                incomeSeed = null; budgetSeed = null; recurringSeed = null
                editor = section
            }, modifier = Modifier.fillMaxWidth(), enabled = !busy && state.categories.isNotEmpty()) {
                Icon(Icons.Outlined.Add, null); Spacer(Modifier.width(8.dp))
                Text(when (section) { "Budgets" -> "Add monthly budget"; "Recurring" -> "Add recurring expense"; else -> "Add income" })
            }
        }
        when (section) {
            "Income" -> {
                val income = state.income.filter { YearMonth.from(Instant.ofEpochMilli(it.timestamp).atZone(ZoneId.systemDefault())) == month }
                if (income.isEmpty()) item { Text("No income recorded for this month. Add salary or another source of income.") }
                items(income, key = { "income-${it.id}" }) { value ->
                    FinanceRecord(value.source.ifBlank { "Income" }, Money.format(value.cents),
                        Instant.ofEpochMilli(value.timestamp).atZone(ZoneId.systemDefault()).toLocalDate().toString()) {
                        incomeSeed = value; editorId = value.id; editor = "Income"
                    }
                }
            }
            "Budgets" -> {
                val budgets = state.budgets.filter { it.month == monthText }
                item { Text("Set one overall budget and optional category budgets. Category limits are compared separately; they are not added to the overall limit.", style = MaterialTheme.typography.bodySmall) }
                if (budgets.isEmpty()) item { Text("No budgets for this month.") }
                items(budgets, key = { "budget-${it.id}" }) { value ->
                    val spent = financeBudgetSpending(value, state.expenses)
                    val label = if (value.categoryId.isEmpty()) "Overall spending" else state.categories.find { it.id == value.categoryId }?.name ?: "Category"
                    FinanceRecord(label, "${Money.format(spent)} of ${Money.format(value.cents)}",
                        if (spent > value.cents) "${Money.format(spent - value.cents)} over budget" else "${Money.format(value.cents - spent)} remaining") {
                        budgetSeed = value; editorId = value.id; editor = "Budgets"
                    }
                }
            }
            else -> {
                item { Text("Payments are created locally when due and reconciled when the app opens. Android may delay background work; missed dates are caught up without duplicate charges.", style = MaterialTheme.typography.bodySmall) }
                if (state.recurring.isEmpty()) item { Text("No recurring expenses. Add a daily, weekly or monthly payment.") }
                items(state.recurring, key = { "recurring-${it.id}" }) { value ->
                    FinanceRecord(value.name, Money.format(value.cents), "${value.frequency} · ${if (value.enabled) "Next ${value.nextDate}" else "Paused"}") {
                        recurringSeed = value; editorId = value.id; editor = "Recurring"
                    }
                }
            }
        }
        if (busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
    }
    when (editor) {
        "Income" -> FinanceIncomeEditor(incomeSeed, editorId, busy, close,
            { vm.saveIncome(it, close) }, incomeSeed?.let { { deleting = "Income" to it.id } })
        "Budgets" -> FinanceBudgetEditor(budgetSeed, editorId, monthText, state.categories, busy, close,
            { vm.saveBudget(it, close) }, budgetSeed?.let { { deleting = "Budgets" to it.id } })
        "Recurring" -> FinanceRecurringEditor(recurringSeed, editorId, state.categories, busy, close,
            { vm.saveRecurring(it, close) }, recurringSeed?.let { { deleting = "Recurring" to it.id } })
    }
    deleting?.let { (kind, id) ->
        DeleteConfirmation("Delete this ${if (kind == "Budgets") "budget" else kind.lowercase()}?",
            if (kind == "Recurring") "Future scheduled payments stop. Previously created expenses stay in your transaction history."
            else "This permanently removes this record. Your other transactions stay available.", busy,
            { deleting = null }, {
                val done = { deleting = null; editor = null }
                when (kind) { "Income" -> vm.deleteIncome(id, done); "Budgets" -> vm.deleteBudget(id, done); else -> vm.deleteRecurring(id, done) }
            })
    }
    operationError?.let { message ->
        AlertDialog(onDismissRequest = vm::dismissError, title = { Text("Unable to update record") }, text = { Text(message) },
            confirmButton = { TextButton(onClick = vm::dismissError) { Text("OK") } })
    }
}

@Composable
private fun FinanceRecord(title: String, value: String, detail: String, edit: () -> Unit) {
    Card(onClick = edit, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(value, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
            Text(detail, style = MaterialTheme.typography.bodySmall)
            Text("Tap to edit or delete", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun FinanceIncomeEditor(seed: FinanceIncome?, id: String, busy: Boolean, dismiss: () -> Unit,
    save: (FinanceIncome) -> Unit, delete: (() -> Unit)?) {
    var amount by rememberSaveable(id) { mutableStateOf(seed?.let { Money.decimal(it.cents) }.orEmpty()) }
    var source by rememberSaveable(id) { mutableStateOf(seed?.source.orEmpty()) }
    var notes by rememberSaveable(id) { mutableStateOf(seed?.notes.orEmpty()) }
    var date by rememberSaveable(id) { mutableStateOf(seed?.let { Instant.ofEpochMilli(it.timestamp).atZone(ZoneId.systemDefault()).toLocalDate() }?.toString() ?: LocalDate.now().toString()) }
    var error by remember { mutableStateOf<String?>(null) }
    EditorShell(if (seed == null) "Add income" else "Edit income", busy, error, dismiss, {
        runCatching {
            FinanceIncome(id, Money.parse(amount), LocalDate.parse(date).atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), source.trim(), notes.trim())
                .also(::validateFinanceIncome)
        }.onSuccess(save).onFailure { error = it.message ?: "Check the income details." }
    }, delete) {
        NumericField(amount, { amount = it }, "Income amount (RM)")
        DateButton(LocalDate.parse(date), "Income date", { date = it.toString() }, Modifier.fillMaxWidth())
        TextField(source, { source = it }, "Income source (optional)", 100)
        TextField(notes, { notes = it }, "Income notes (optional)", 2000, true)
    }
}

@Composable
private fun FinanceBudgetEditor(seed: FinanceBudget?, id: String, selectedMonth: String, categories: List<Category>, busy: Boolean,
    dismiss: () -> Unit, save: (FinanceBudget) -> Unit, delete: (() -> Unit)?) {
    var amount by rememberSaveable(id) { mutableStateOf(seed?.let { Money.decimal(it.cents) }.orEmpty()) }
    var month by rememberSaveable(id) { mutableStateOf(seed?.month ?: selectedMonth) }
    var category by rememberSaveable(id) { mutableStateOf(seed?.categoryId.orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }
    EditorShell(if (seed == null) "Add monthly budget" else "Edit monthly budget", busy, error, dismiss, {
        runCatching { FinanceBudget(id, month.trim(), Money.parse(amount), category).also(::validateFinanceBudget) }
            .onSuccess(save).onFailure { error = it.message ?: "Check the budget details." }
    }, delete) {
        TextField(month, { month = it }, "Budget month (YYYY-MM)", 7)
        NumericField(amount, { amount = it }, "Spending limit (RM)")
        Choice("Budget scope", category, listOf("" to "Overall spending") + categories.map { it.id to it.name }) { category = it }
        Text("A budget is a spending plan. It does not create income or change your transactions.", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun FinanceRecurringEditor(seed: FinanceRecurring?, id: String, categories: List<Category>, busy: Boolean,
    dismiss: () -> Unit, save: (FinanceRecurring) -> Unit, delete: (() -> Unit)?) {
    var name by rememberSaveable(id) { mutableStateOf(seed?.name.orEmpty()) }
    var amount by rememberSaveable(id) { mutableStateOf(seed?.let { Money.decimal(it.cents) }.orEmpty()) }
    var category by rememberSaveable(id) { mutableStateOf(seed?.categoryId ?: categories.firstOrNull()?.id.orEmpty()) }
    var frequency by rememberSaveable(id) { mutableStateOf(seed?.frequency ?: "Monthly") }
    var start by rememberSaveable(id) { mutableStateOf(seed?.startDate ?: LocalDate.now().toString()) }
    var enabled by rememberSaveable(id) { mutableStateOf(seed?.enabled ?: true) }
    var payment by rememberSaveable(id) { mutableStateOf(seed?.payment ?: "Cash") }
    var notes by rememberSaveable(id) { mutableStateOf(seed?.notes.orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }
    EditorShell(if (seed == null) "Add recurring expense" else "Edit recurring expense", busy, error, dismiss, {
        runCatching {
            val next = if (seed != null && seed.startDate == start && seed.frequency == frequency) seed.nextDate else start
            FinanceRecurring(id, name.trim(), Money.parse(amount), category, frequency, start, next, enabled, payment, notes.trim())
                .also(::validateFinanceRecurring)
        }.onSuccess(save).onFailure { error = it.message ?: "Check the recurring payment details." }
    }, delete) {
        TextField(name, { name = it }, "Recurring expense name", 200)
        NumericField(amount, { amount = it }, "Recurring payment (RM)")
        Choice("Category", category, categories.map { it.id to it.name }) { category = it }
        Choice("Frequency", frequency, financeFrequencies.map { it to it }) { frequency = it }
        DateButton(LocalDate.parse(start), "First payment date", { start = it.toString() }, Modifier.fillMaxWidth())
        Choice("Payment method", payment, paymentMethods.map { it to it }) { payment = it }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Scheduled payments enabled", Modifier.weight(1f)); Switch(enabled, { enabled = it })
        }
        TextField(notes, { notes = it }, "Recurring notes (optional)", 2000, true)
        Text("Saving a past start date records missed payments through today. Monthly rules keep the original day after shorter months. Paused rules catch up when enabled again.", style = MaterialTheme.typography.bodySmall)
    }
}
