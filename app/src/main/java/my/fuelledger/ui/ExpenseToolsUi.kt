package my.fuelledger.ui

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.background
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
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
                listOf("Edit", "Duplicate", "Delete").filterNot { it == "Duplicate" && expense.fuelId != null }.forEach { label -> DropdownMenuItem({ Text(label) }, { menu = false; action(label) }) }
                if (expense.kind == "Expense") DropdownMenuItem({ Text("Record refund") }, { menu = false; refund(expense) })
                if (vm != null && expense.kind == "Expense" && expense.categoryId != FUEL_CATEGORY) DropdownMenuItem({ Text("Favourite") }, {
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

@Composable fun ExpenseFavourites(vm: LedgerViewModel, state: LedgerState, review: (Expense) -> Unit, favourites: List<FavouriteExpense>, refuel: () -> Unit = {}) {
    var editing by remember { mutableStateOf<FavouriteExpense?>(null) }
    var deleting by remember { mutableStateOf<FavouriteExpense?>(null) }
    if (favourites.isNotEmpty()) {
        Text("Favourites", style = MaterialTheme.typography.titleMedium)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            favourites.forEach { value ->
                Column {
                    FilledTonalButton(onClick = {
                        val e = value.expense().copy(id = UUID.randomUUID().toString(), timestamp = System.currentTimeMillis())
                        if (e.categoryId == FUEL_CATEGORY) refuel() else if (likelyDuplicates(e, state.expenses).isEmpty()) vm.saveExpense(e) {} else review(e)
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

/** One month grid and one local-date transaction source for Home, Expenses and the detail page. */
@Composable fun SpendingCalendarMonth(state: LedgerState, month: YearMonth, selected: LocalDate,
    today: LocalDate, zone: ZoneId, select: (LocalDate) -> Unit, changeMonth: (YearMonth) -> Unit) {
    val privacy = LocalUiPreferences.current.privacyMode
    val days = remember(state.expenses, zone) { state.expenses.groupBy { Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalDate() } }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton({ changeMonth(month.minusMonths(1)) }, enabled = month.year > 1970) { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, "Previous calendar month") }
            Text(month.format(java.time.format.DateTimeFormatter.ofPattern("MMMM yyyy", my.fuelledger.domain.Malaysia)), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            IconButton({ changeMonth(month.plusMonths(1)) }, enabled = month.year < 2999) { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, "Next calendar month") }
        }
        Row { listOf("M", "T", "W", "T", "F", "S", "S").forEach { Text(it, Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center, style = MaterialTheme.typography.labelSmall) } }
        androidx.compose.animation.Crossfade(month, animationSpec = androidx.compose.animation.core.tween(if (LocalPressMotionEnabled.current) 120 else 0), label = "Calendar month") { displayedMonth ->
            val offset = displayedMonth.atDay(1).dayOfWeek.value - 1
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                repeat((offset + displayedMonth.lengthOfMonth() + 6) / 7) { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        repeat(7) { col ->
                            val day = row * 7 + col - offset + 1
                            if (day !in 1..displayedMonth.lengthOfMonth()) Spacer(Modifier.weight(1f)) else {
                                val date = displayedMonth.atDay(day); val values = days[date].orEmpty(); val cents = values.sumOf { it.cents }
                                Card(onClick = { select(date) }, Modifier.weight(1f).heightIn(min = 48.dp).testTag("calendar-date-$date")
                                    .semantics { contentDescription = maskAmounts("${dateLabel(date)}, ${Money.format(cents)}${if (date == today) ", Today" else ""}", privacy); this.selected = date == selected },
                                    shape = MaterialTheme.shapes.small,
                                    border = if (date == today) androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
                                    colors = CardDefaults.cardColors(containerColor = if (date == selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer)) {
                                    Column(Modifier.fillMaxWidth().padding(vertical = 5.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(day.toString(), style = MaterialTheme.typography.labelLarge)
                                        Text(if (values.isEmpty()) "" else if (LocalUiPreferences.current.privacyMode) "•••" else compactCalendarAmount(cents),
                                            style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                        if (values.isNotEmpty()) Box(Modifier.size(3.dp).background(MaterialTheme.colorScheme.primary, androidx.compose.foundation.shape.CircleShape))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

internal fun compactCalendarAmount(cents: Long): String = when {
    kotlin.math.abs(cents) >= 100_000_000 -> "${number(cents / 100_000_000.0, 1)}m"
    kotlin.math.abs(cents) >= 100_000 -> "${number(cents / 100_000.0, 1)}k"
    else -> Money.decimal(cents)
}

/** Compact embedded calendar. Selecting a day exposes exact totals and its actual records. */
@Composable fun EmbeddedSpendingCalendar(state: LedgerState, edit: (Expense) -> Unit, openDay: (LocalDate) -> Unit) {
    val clock = currentCalendar()
    var monthText by rememberSaveable { mutableStateOf(YearMonth.from(clock.today).toString()) }
    var selectedText by rememberSaveable { mutableStateOf(clock.today.toString()) }
    var expanded by rememberSaveable { mutableStateOf(false) }
    var lastToday by rememberSaveable { mutableStateOf(clock.today.toString()) }
    LaunchedEffect(clock.today) {
        val previousToday = LocalDate.parse(lastToday)
        if (selectedText == lastToday && monthText == YearMonth.from(previousToday).toString()) {
            monthText = YearMonth.from(clock.today).toString(); selectedText = clock.today.toString()
        }
        lastToday = clock.today.toString()
    }
    val month = YearMonth.parse(monthText); val selected = LocalDate.parse(selectedText)
    val entries = remember(state.expenses, selected, clock.zone) { state.expenses.filter { Instant.ofEpochMilli(it.timestamp).atZone(clock.zone).toLocalDate() == selected }.sortedByDescending { it.timestamp } }
    LedgerCard {
        Text("Spending Calendar", style = MaterialTheme.typography.titleMedium)
        SpendingCalendarMonth(state, month, selected, clock.today, clock.zone,
            { selectedText = it.toString(); expanded = true }, { monthText = it.toString(); selectedText = it.atDay(1).toString(); expanded = false })
        if (expanded) {
            Text("${dateLabel(selected)} · ${Money.format(entries.sumOf { it.cents })}", style = MaterialTheme.typography.titleSmall)
            entries.take(3).forEach { e -> ExpenseRow(e, state.categories.find { it.id == e.categoryId }) { edit(e) } }
            if (entries.isEmpty()) Text("No recorded transactions", style = MaterialTheme.typography.bodySmall)
            TextButton({ openDay(selected) }) { Text(if (entries.size > 3) "View all ${entries.size} transactions" else "View day / add expense") }
        }
    }
}

@Composable fun SpendingCalendar(state: LedgerState, edit: (Expense) -> Unit, add: (LocalDate) -> Unit, initialDate: LocalDate? = null) {
    val clock = currentCalendar()
    var monthText by rememberSaveable { mutableStateOf(YearMonth.from(initialDate ?: clock.today).toString()) }
    var selectedText by rememberSaveable { mutableStateOf((initialDate ?: clock.today).toString()) }
    val month = YearMonth.parse(monthText); val selected = LocalDate.parse(selectedText)
    val summary = remember(state.expenses, month, clock) { calendarSummary(state.expenses, month, clock.zone, clock.today) }
    val entries = remember(state.expenses, selected, clock.zone) { state.expenses.filter { Instant.ofEpochMilli(it.timestamp).atZone(clock.zone).toLocalDate() == selected }.sortedByDescending { it.timestamp } }
    LazyColumn(contentPadding = ledgerScreenPadding, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { SpendingCalendarMonth(state, month, selected, clock.today, clock.zone,
            { selectedText = it.toString() }, { monthText = it.toString(); selectedText = it.atDay(1).toString() }) }
        item { Stat("Monthly spending", Money.format(summary.total), Modifier.fillMaxWidth(), "Highest: ${summary.highestDay?.toString() ?: "—"} · Average ${Money.format(kotlin.math.round(summary.dailyAverageSen).toLong())}/day") }
        item { SectionTitle(dateLabel(selected), "${Money.format(entries.sumOf { it.cents })} net spending") }
        item { LedgerAction("Add for this date", ledgerSymbol("add"), Modifier.fillMaxWidth()) { add(selected) } }
        if (entries.isEmpty()) item { Text("No recorded transactions", style = MaterialTheme.typography.bodyMedium) }
        items(entries, key = { it.id }) { e -> ExpenseRow(e, state.categories.find { it.id == e.categoryId }) { edit(e) } }
    }
}
