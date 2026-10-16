package my.fuelledger.ui

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import my.fuelledger.LedgerState
import my.fuelledger.LedgerViewModel
import my.fuelledger.data.*
import my.fuelledger.domain.Money
import java.time.*
import java.util.UUID

@Composable fun TransactionUndoBar(vm: LedgerViewModel, current: TransactionUndo?) {
    current?.let { entry ->
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Transaction ${if (entry.change().after == null) "deleted" else "updated"}", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
            TextButton({ vm.undoTransaction(entry.id) }, modifier = Modifier.testTag("transaction-undo-action")) { Text("Undo") }
        }
    }
}

@Composable fun TransactionActions(expense: Expense, state: LedgerState, edit: (Expense) -> Unit,
    duplicate: (Expense) -> Unit, refund: (Expense) -> Unit, vm: LedgerViewModel?) {
    var menu by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    val threshold = with(LocalDensity.current) { 80.dp.toPx() }
    fun action(name: String) { when (name) { "Edit" -> edit(expense); "Duplicate" -> duplicate(expense); "Delete" -> deleting = true } }
    val gesture = if (vm == null || state.settings.swipeStart == "None" && state.settings.swipeEnd == "None") Modifier else Modifier.pointerInput(expense, state.settings.swipeStart, state.settings.swipeEnd) {
        var distance = 0f
        detectHorizontalDragGestures(onDragStart = { distance = 0f }, onDragEnd = {
            if (distance >= threshold) action(state.settings.swipeStart)
            else if (distance <= -threshold) action(state.settings.swipeEnd)
        }, onDragCancel = { distance = 0f }) { change, drag -> change.consume(); distance += drag }
    }
    Column(Modifier.testTag("transaction-${expense.id}").then(gesture).semantics { customActions = listOf(CustomAccessibilityAction("Transaction actions") { menu = true; true }) }) {
        ExpenseRow(expense, state.categories.find { it.id == expense.categoryId }) { edit(expense) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            if (expense.kind == "Refund") Text("Refund", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            TextButton({ menu = true }, contentPadding = PaddingValues(horizontal = 8.dp)) { Text("Actions", style = MaterialTheme.typography.labelSmall) }
            DropdownMenu(menu, { menu = false }) {
                listOf("Edit", "Duplicate", "Delete").forEach { label -> DropdownMenuItem({ Text(label) }, { menu = false; action(label) }) }
                if (expense.kind == "Expense") DropdownMenuItem({ Text("Record refund") }, { menu = false; refund(expense) })
                if (vm != null && expense.kind == "Expense") DropdownMenuItem({ Text("Favourite") }, {
                    menu = false; vm.saveFavourite(favourite(UUID.randomUUID().toString(), expense.description.ifBlank { expense.merchant.ifBlank { "Favourite" } }.take(100), expense))
                })
            }
        }
    }
    if (deleting) DeleteConfirmation("Delete transaction?", if (expense.fuelId == null) "Undo is available in Expenses, including after reopening the app." else "This is a refuel payment. Delete it from its refuel editor to keep fuel history consistent.", false, { deleting = false }) {
        deleting = false
        if (expense.fuelId != null) edit(expense) else vm?.deleteExpense(expense.id) {}
    }
}

@Composable fun ExpenseFavourites(vm: LedgerViewModel, state: LedgerState, review: (Expense) -> Unit, favourites: List<FavouriteExpense>) {
    var editing by remember { mutableStateOf<FavouriteExpense?>(null) }
    var deleting by remember { mutableStateOf<FavouriteExpense?>(null) }
    if (favourites.isNotEmpty()) {
        Text("Favourites", style = MaterialTheme.typography.titleMedium)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            favourites.forEach { value ->
                Column {
                    FilledTonalButton(onClick = {
                        val e = value.expense().copy(id = UUID.randomUUID().toString(), timestamp = System.currentTimeMillis())
                        if (likelyDuplicates(e, state.expenses).isEmpty()) vm.saveExpense(e) {} else review(e)
                    }, modifier = Modifier.testTag("favourite-${value.id}")) { Text("${value.name} · ${Money.format(value.expense().cents)}") }
                    Row { TextButton({ editing = value }) { Text("Edit") }; TextButton({ deleting = value }) { Text("Remove") } }
                }
            }
        }
    }
    editing?.let { original ->
        var name by remember(original.id) { mutableStateOf(original.name) }
        var description by remember(original.id) { mutableStateOf(original.expense().description) }
        var merchant by remember(original.id) { mutableStateOf(original.expense().merchant) }
        var notes by remember(original.id) { mutableStateOf(original.expense().notes) }
        var amount by remember(original.id) { mutableLongStateOf(original.expense().cents) }
        var category by remember(original.id) { mutableStateOf(original.expense().categoryId) }
        var payment by remember(original.id) { mutableStateOf(original.expense().payment) }
        var error by remember { mutableStateOf<String?>(null) }
        EditorShell("Edit favourite", false, error, { editing = null }, {
            runCatching { favourite(original.id, name, original.expense().copy(cents = amount, categoryId = category, payment = payment, description = description, merchant = merchant, notes = notes)) }
                .onSuccess { vm.saveFavourite(it) { editing = null } }.onFailure { error = it.message }
        }, saveEnabled = amount > 0 && expensePaymentGroup(payment) != null) {
            TextField(name, { name = it }, "Name", 100); ExpenseAmount(amount)
            ExpenseCategoryRow(state.categories, category, true) { category = it }; ExpensePaymentRow(payment, true) { payment = it }
            CentsKeypad(amount, true, { amount = it }, {})
            ExpandableHelp("Favourite details") { TextField(merchant, { merchant = it }, "Merchant", 200); TextField(description, { description = it }, "Description", 200); TextField(notes, { notes = it }, "Notes", 2000, true) }
        }
    }
    deleting?.let { value -> DeleteConfirmation("Remove favourite?", "Existing transactions are kept.", false, { deleting = null }) { vm.deleteFavourite(value.id); deleting = null } }
}

@Composable fun SpendingCalendar(state: LedgerState, edit: (Expense) -> Unit, add: (LocalDate) -> Unit) {
    val clock = currentCalendar()
    var monthText by rememberSaveable { mutableStateOf(YearMonth.from(clock.today).toString()) }
    var selectedText by rememberSaveable { mutableStateOf(clock.today.toString()) }
    val month = YearMonth.parse(monthText)
    val selected = LocalDate.parse(selectedText)
    val summary = calendarSummary(state.expenses, month, clock.zone, clock.today)
    val grouped = state.expenses.groupBy { Instant.ofEpochMilli(it.timestamp).atZone(clock.zone).toLocalDate() }
    LazyColumn(contentPadding = ledgerScreenPadding, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton({ monthText = month.minusMonths(1).toString(); selectedText = month.minusMonths(1).atDay(1).toString() }, enabled = month.year > 1970) { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, "Previous calendar month") }
                Text(month.toString(), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                IconButton({ monthText = month.plusMonths(1).toString(); selectedText = month.plusMonths(1).atDay(1).toString() }, enabled = month.year < 2999) { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, "Next calendar month") }
            }
        }
        item {
            val offset = month.atDay(1).dayOfWeek.value - 1
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row { listOf("M", "T", "W", "T", "F", "S", "S").forEach { Text(it, Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center, style = MaterialTheme.typography.labelSmall) } }
                repeat((offset + month.lengthOfMonth() + 6) / 7) { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        repeat(7) { col ->
                            val day = row * 7 + col - offset + 1
                            if (day !in 1..month.lengthOfMonth()) Spacer(Modifier.weight(1f)) else {
                                val date = month.atDay(day); val values = grouped[date].orEmpty()
                                Surface(onClick = { selectedText = date.toString() }, Modifier.weight(1f).heightIn(min = 60.dp).testTag("calendar-date-$date"), shape = MaterialTheme.shapes.small,
                                    color = if (date == selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer) {
                                    Column(Modifier.padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(day.toString(), style = MaterialTheme.typography.labelLarge)
                                        if (values.isNotEmpty()) Text(if (LocalUiPreferences.current.privacyMode) "••••" else Money.decimal(values.sumOf { it.cents }), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        item { Stat("Monthly spending", Money.format(summary.total), Modifier.fillMaxWidth(), "Highest: ${summary.highestDay?.toString() ?: "—"} · Average ${Money.format(kotlin.math.round(summary.dailyAverageSen).toLong())}/day") }
        item { SectionTitle(dateLabel(selected), "${Money.format(grouped[selected].orEmpty().sumOf { it.cents })} net spending") }
        item { LedgerAction("Add for this date", ledgerSymbol("add"), Modifier.fillMaxWidth()) { add(selected) } }
        if (grouped[selected].isNullOrEmpty()) item { Text("No recorded transactions", style = MaterialTheme.typography.bodyMedium) }
        items(grouped[selected].orEmpty().sortedByDescending { it.timestamp }, key = { it.id }) { e -> ExpenseRow(e, state.categories.find { it.id == e.categoryId }) { edit(e) } }
    }
}
