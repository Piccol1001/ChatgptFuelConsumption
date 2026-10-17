package my.fuelledger.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import my.fuelledger.LedgerViewModel
import my.fuelledger.data.*
import my.fuelledger.domain.*
import java.time.*

@Composable
fun EditorShell(title: String, busy: Boolean, error: String?, dismiss: () -> Unit, save: () -> Unit,
    delete: (() -> Unit)? = null, saveEnabled: Boolean = true, compact: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    val interaction = rememberAppLockInteraction()
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    Dialog(onDismissRequest = { if (!busy) dismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false, securePolicy = SecureFlagPolicy.SecureOff)) {
        PrivacyDialogWindow()
        val width = if (compact) Modifier.widthIn(max = 560.dp).fillMaxWidth() else Modifier.fillMaxWidth().widthIn(max = 560.dp)
        Surface(width.padding(if (compact) 8.dp else 12.dp).heightIn(max = if (compact) 860.dp else 720.dp), shape = MaterialTheme.shapes.large) {
            Column(Modifier.imePadding().padding(if (compact) 16.dp else 20.dp), verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    if (compact) TextButton(onClick = { if (interaction() && !busy) dismiss() }, enabled = !busy) { Text("Cancel") }
                }
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp), content = content)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
                Button(onClick = { if (interaction()) { haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.Confirm); save() } }, enabled = !busy && saveEnabled, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(if (busy) "Saving…" else "Save")
                }
                if (!compact || delete != null) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (delete != null) TextButton(onClick = { if (interaction()) delete() }, enabled = !busy, modifier = Modifier.weight(1f)) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                    if (!compact) TextButton(onClick = { if (interaction()) dismiss() }, enabled = !busy, modifier = Modifier.weight(1f)) { Text("Cancel") }
                }
            }
        }
    }
}
@Composable
fun NumericField(value: String, change: (String) -> Unit, label: String) {
    val interaction = rememberAppLockInteraction()
    OutlinedTextField(value, { if (interaction() && it.length <= 24) change(it) }, label = { Text(label) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
}
@Composable
fun TextField(value: String, change: (String) -> Unit, label: String, max: Int, multiline: Boolean = false) {
    val interaction = rememberAppLockInteraction()
    OutlinedTextField(value, { if (interaction() && it.length <= max) change(it) }, label = { Text(label) }, singleLine = !multiline,
        minLines = if (multiline) 2 else 1, maxLines = if (multiline) 4 else 1, modifier = Modifier.fillMaxWidth())
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Choice(label: String, selected: String, values: List<Pair<String, String>>, change: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val locked = LocalAppLocked.current
    val interaction = rememberAppLockInteraction()
    LaunchedEffect(locked) { if (locked) open = false }
    ExposedDropdownMenuBox(expanded = open && !locked, onExpandedChange = { if (interaction()) open = !open }) {
        OutlinedTextField(values.firstOrNull { it.first == selected }?.second ?: selected, {}, readOnly = true,
            label = { Text(label) }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(open) },
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable))
        ExposedDropdownMenu(expanded = open && !locked, onDismissRequest = { open = false }) {
            values.forEach { (id, name) -> DropdownMenuItem(text = { Text(name) }, onClick = { if (interaction()) change(id); open = false }) }
        }
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateButton(date: LocalDate, label: String, change: (LocalDate) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    val interaction = rememberAppLockInteraction()
    OutlinedButton(onClick = { if (interaction()) open = true }, modifier = modifier) { Text("$label: ${dateLabel(date)}") }
    if (open) {
        val picker = rememberDatePickerState(initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(onDismissRequest = { open = false },
            confirmButton = { TextButton(onClick = {
                if (interaction()) picker.selectedDateMillis?.let { change(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                open = false
            }) { Text("Select") } }, dismissButton = { TextButton(onClick = { open = false }) { Text("Cancel") } }) {
            PrivacyDialogWindow()
            DatePicker(picker)
        }
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateTimeFields(date: String, time: String, changeDate: (String) -> Unit, changeTime: (String) -> Unit) {
    var timeOpen by rememberSaveable { mutableStateOf(false) }
    val interaction = rememberAppLockInteraction()
    DateButton(LocalDate.parse(date), "Date", { changeDate(it.toString()) }, Modifier.fillMaxWidth())
    OutlinedButton(onClick = { if (interaction()) timeOpen = true }, modifier = Modifier.fillMaxWidth()) { Text("Time: $time · device local time") }
    if (timeOpen) {
        val selected = LocalTime.parse(time)
        val picker = rememberTimePickerState(initialHour = selected.hour, initialMinute = selected.minute, is24Hour = true)
        LaunchedEffect(picker.hour, picker.minute) { interaction() }
        AlertDialog(onDismissRequest = { timeOpen = false }, title = { Text("Time") },
            properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOff),
            text = { PrivacyDialogWindow(); TimeInput(picker) },
            confirmButton = { TextButton(onClick = { if (interaction()) { changeTime(LocalTime.of(picker.hour, picker.minute).toString()); timeOpen = false } }) { Text("Select") } },
            dismissButton = { TextButton(onClick = { timeOpen = false }) { Text("Cancel") } })
    }
}
fun timestamp(date: String, time: String) = LocalDate.parse(date).atTime(LocalTime.parse(time)).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

@Composable
fun ExpenseEditor(seed: Expense?, id: String, categories: List<Category>, busy: Boolean, vm: LedgerViewModel, dismiss: () -> Unit,
    save: (Expense) -> Unit, delete: () -> Unit, draft: Expense? = null, initialDate: LocalDate? = null) {
    val starting = seed ?: draft
    val ledger by vm.state.collectAsStateWithLifecycle()
    val frequent = remember(ledger.expenses) { ledger.expenses.filter { it.kind == "Expense" }.groupingBy { it.categoryId }.eachCount().filterValues { it >= 3 }.entries.sortedByDescending { it.value }.take(3).map { it.key } }
    val initial = remember(id) { Instant.ofEpochMilli(starting?.timestamp ?: initialDate?.atTime(12, 0)?.atZone(ZoneId.systemDefault())?.toInstant()?.toEpochMilli() ?: System.currentTimeMillis()).atZone(ZoneId.systemDefault()) }
    var amount by rememberSaveable(id) { mutableLongStateOf(kotlin.math.abs(starting?.cents ?: 0L)) }
    var category by rememberSaveable(id) { mutableStateOf(starting?.categoryId?.takeIf { it != FUEL_CATEGORY || seed != null || starting.kind == "Refund" } ?: vm.state.value.settings.lastExpenseCategory.takeIf { id -> manualExpenseCategories(categories).any { it.id == id } } ?: manualExpenseCategories(categories).first().id) }
    var date by rememberSaveable(id) { mutableStateOf(initial.toLocalDate().toString()) }
    var time by rememberSaveable(id) { mutableStateOf(initial.toLocalTime().withSecond(0).withNano(0).toString()) }
    var description by rememberSaveable(id) { mutableStateOf(starting?.description.orEmpty()) }
    var kind by rememberSaveable(id) { mutableStateOf(starting?.kind ?: "Expense") }
    var merchant by rememberSaveable(id) { mutableStateOf(starting?.merchant.orEmpty()) }
    var categoryExplicit by rememberSaveable(id) { mutableStateOf(seed != null) }
    var paymentExplicit by rememberSaveable(id) { mutableStateOf(seed != null) }
    val merchants by vm.receiptMerchants.collectAsStateWithLifecycle()
    val suggestion = merchants.firstOrNull { merchantKey(merchant) == it.merchant }
    var duplicate by remember { mutableStateOf<Expense?>(null) }
    var favouriteName by rememberSaveable { mutableStateOf("") }
    var favouriteOpen by rememberSaveable { mutableStateOf(false) }
    var notes by rememberSaveable(id) { mutableStateOf(starting?.notes.orEmpty()) }
    var payment by rememberSaveable(id) { mutableStateOf(starting?.payment ?: vm.state.value.settings.lastExpensePayment) }
    var receiptFile by rememberSaveable(id) { mutableStateOf(seed?.receiptFile) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var details by rememberSaveable(id) { mutableStateOf(false) }
    val interaction = rememberAppLockInteraction()
    EditorShell(if (kind == "Refund") "Record refund" else if (seed == null) "Add expense" else "Edit expense", busy, error, dismiss, {
        runCatching {
            require(expensePaymentGroup(payment) != null) { "Choose a payment method." }
            val savedTime = if (seed != null && date == initial.toLocalDate().toString() && time == initial.toLocalTime().withSecond(0).withNano(0).toString()) seed.timestamp else timestamp(date, time)
            Expense(id, if (kind == "Refund") -amount else amount, category, savedTime, description.trim(), notes.trim(), payment,
            fuelId = seed?.fuelId, receiptFile = receiptFile, recurringRuleId = seed?.recurringRuleId, recurringDate = seed?.recurringDate, merchant = merchant.trim(), kind = kind, refundOf = if (kind == "Refund") starting?.refundOf else null).also(::validateExpense) }
            .onSuccess { error = null; if (likelyDuplicates(it, vm.state.value.expenses).isEmpty()) save(it) else duplicate = it }.onFailure { error = it.message }
    }, if (seed != null) ({ confirmDelete = true }) else null,
        saveEnabled = amount > 0 && expensePaymentGroup(payment) != null && categories.any { it.id == category }, compact = true) {
        ExpenseAmount(amount)
        if (category == FUEL_CATEGORY) Text("Existing category: Fuel & Transport", style = MaterialTheme.typography.bodySmall)
        ExpenseCategoryRow(categories, category, !busy, preferred = frequent) { category = it; categoryExplicit = true }
        ExpensePaymentRow(payment, !busy) { selected ->
            paymentExplicit = true
            payment = selectExpensePayment(payment, selected)
            vm.rememberExpensePayment(selected)
        }
        if (expensePaymentGroup(payment) == null) Text("This record has no payment method. Choose one to save changes.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        CentsKeypad(amount, !busy, { amount = it; error = null }, { error = "Maximum amount is ${Money.format(MAX_CENTS)}." })
        TextButton(onClick = { if (interaction()) details = !details }, modifier = Modifier.fillMaxWidth()) { Text(if (details) "Hide details & receipt" else "Details & receipt") }
        if (details) {
            if (seed == null && starting?.refundOf == null) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Expense", "Refund").forEach { value -> FilterChip(kind == value, { kind = value }, label = { Text(value) }) }
            }
            DateTimeFields(date, time, { date = it }, { time = it })
            TextField(merchant, { merchant = it }, "Merchant (optional)", 200)
            suggestion?.let { memory ->
                TextButton({
                    if (!categoryExplicit && categories.any { it.id == memory.categoryId }) category = memory.categoryId
                    if (!paymentExplicit && memory.payment.isNotEmpty()) payment = memory.payment
                    if (description.isBlank()) description = memory.description
                }) { Text("Use remembered merchant details") }
            }
            TextField(description, { description = it }, "Description (optional)", 200)
            ExpandableHelp("Notes") { TextField(notes, { notes = it }, "Notes (optional)", 2000, true) }
            TextButton({ favouriteOpen = true; favouriteName = description.ifBlank { merchant } }, enabled = kind == "Expense") { Text("Save as favourite") }
        }
        // Keep attachment ownership/cleanup composed when its controls are collapsed.
        ReceiptAttachment(id, receiptFile, vm, busy, { receiptFile = it }, categories = categories, fuelReceipt = false, visible = details) { scanned ->
            scanned.amount?.let { value -> runCatching { Money.parse(value) }.onSuccess { amount = it }.onFailure { error = "Check the scanned amount." } }
            scanned.station?.let { description = it }
            scanned.date?.let { date = it }; scanned.time?.let { time = it }
            scanned.suggestedCategoryId?.takeIf { suggested -> categories.any { it.id == suggested } }?.let { category = it }
        }
    }
    duplicate?.let { candidate -> AlertDialog(onDismissRequest = { duplicate = null }, title = { Text("Possible duplicate") },
        text = { PrivacyDialogWindow(); Text("A similar amount, merchant and time or receipt already exists. Save only if this is another transaction.") },
        confirmButton = { TextButton({ duplicate = null; save(candidate) }) { Text("Save anyway") } }, dismissButton = { TextButton({ duplicate = null }) { Text("Review") } }) }
    if (favouriteOpen) EditorShell("Save favourite", busy, error, { favouriteOpen = false }, {
        runCatching { favourite(java.util.UUID.randomUUID().toString(), favouriteName.trim(), Expense(id, amount, category, initial.toInstant().toEpochMilli(), description, notes, payment, merchant = merchant)) }
            .onSuccess { vm.saveFavourite(it) { favouriteOpen = false } }.onFailure { error = it.message }
    }) { TextField(favouriteName, { favouriteName = it }, "Name", 100) }
    if (confirmDelete) DeleteConfirmation("Delete expense?", "You can restore it with Undo in Expenses.", busy, { confirmDelete = false }, delete)
}

@Composable
fun RefuelEditor(seed: Refuel?, id: String, busy: Boolean, vm: LedgerViewModel, dismiss: () -> Unit, save: (Refuel) -> Unit, delete: () -> Unit) {
    val initial = remember(id) { Instant.ofEpochMilli(seed?.timestamp ?: System.currentTimeMillis()).atZone(ZoneId.systemDefault()) }
    var odometer by rememberSaveable(id) { mutableStateOf(seed?.odometer?.toString().orEmpty()) }
    var litres by rememberSaveable(id) { mutableStateOf(seed?.litres?.toString().orEmpty()) }
    var amount by rememberSaveable(id) { mutableLongStateOf(seed?.cents ?: 0L) }
    var date by rememberSaveable(id) { mutableStateOf(initial.toLocalDate().toString()) }
    var time by rememberSaveable(id) { mutableStateOf(initial.toLocalTime().withSecond(0).withNano(0).toString()) }
    var station by rememberSaveable(id) { mutableStateOf(seed?.station.orEmpty()) }
    var stationLocation by rememberSaveable(id) { mutableStateOf(seed?.stationLocation.orEmpty()) }
    var fuelType by rememberSaveable(id) { mutableStateOf(seed?.fuelType ?: "RON95") }
    var receiptFile by rememberSaveable(id) { mutableStateOf(seed?.receiptFile) }
    var details by rememberSaveable(id) { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    EditorShell(if (seed == null) "Add refuel" else "Edit refuel", busy, error, dismiss, {
        runCatching {
            val savedTime = if (seed != null && date == initial.toLocalDate().toString() && time == initial.toLocalTime().withSecond(0).withNano(0).toString()) seed.timestamp else timestamp(date, time)
            Refuel(id, savedTime, measurement(odometer, "Odometer", true), measurement(litres, "Litres"), amount,
                seed?.fullTank ?: false, station.trim(), seed?.notes.orEmpty(), fuelType, stationLocation, receiptFile).also(::validateRefuel)
        }.onSuccess { error = null; save(it) }.onFailure { error = it.message }
    }, if (seed != null) ({ confirmDelete = true }) else null, saveEnabled = amount > 0) {
        DateTimeFields(date, time, { date = it }, { time = it })
        NumericField(odometer, { odometer = it }, "Odometer (km)")
        NumericField(litres, { litres = it }, "Fuel quantity (L)")
        Text("Fuel type: ${fuelType.ifBlank { "Not recorded" }}", style = MaterialTheme.typography.bodySmall)
        ExpenseAmount(amount)
        val price = runCatching { require(amount > 0); amount / 100.0 / measurement(litres, "Litres") }.getOrNull()
        if (price != null) Text("Price per litre: RM ${number(price, 3)}/L", color = MaterialTheme.colorScheme.primary)
        CentsKeypad(amount, !busy, { amount = it; error = null }, { error = "Maximum amount is ${Money.format(MAX_CENTS)}." })
        val state by vm.state.collectAsStateWithLifecycle()
        val enteredTime = runCatching { timestamp(date, time) }.getOrNull()
        val previous = state.refuels.filter { it.id != id && enteredTime != null && it.timestamp < enteredTime }.maxByOrNull { it.timestamp }
        val estimate = runCatching {
            val point = FuelPoint(id, enteredTime!!, measurement(odometer, "Odometer", true), measurement(litres, "Litres"), amount, seed?.fullTank ?: false)
            previous?.let { fillEstimates(listOf(it.point(), point)).singleOrNull() }
        }.getOrNull()
        if (estimate != null) {
            Text("Estimated Fuel Consumption · ${number(estimate.kmPerLitre)} km/L", style = MaterialTheme.typography.titleSmall)
            Text("${number(estimate.litresPer100Km)} L/100 km · ${number(estimate.distance, 1)} km since previous fill", style = MaterialTheme.typography.bodySmall)
        }
        ExpandableHelp("Estimate assumptions") { Text(FUEL_ESTIMATE_ASSUMPTION, style = MaterialTheme.typography.bodySmall) }
        TextButton({ details = !details }, Modifier.fillMaxWidth()) { Text(if (details) "Hide receipt & station" else "Receipt & station") }
        if (details) TextField(station, { station = it }, "Petrol station (optional)", 100)
        ReceiptAttachment(id, receiptFile, vm, busy, { receiptFile = it }, visible = details) { scanned ->
            scanned.litres?.let { litres = it }; scanned.amount?.let { runCatching { Money.parse(it) }.onSuccess { amount = it } }
            scanned.odometer?.let { odometer = it }; scanned.fuelType?.let { fuelType = it }
            scanned.station?.let { station = it }; scanned.stationLocation?.let { stationLocation = it }
            scanned.date?.let { date = it }; scanned.time?.let { time = it }
        }
    }
    if (confirmDelete) DeleteConfirmation("Delete refuel?", "This removes the refuel and its linked expense. Consumption estimates will be recalculated; Undo is available in Expenses.", busy, { confirmDelete = false }, delete)
}
@Composable
fun DeleteConfirmation(title: String, detail: String, busy: Boolean, cancel: () -> Unit, confirm: () -> Unit) {
    val interaction = rememberAppLockInteraction()
    AlertDialog(onDismissRequest = { if (!busy) cancel() }, title = { Text(title) }, text = { PrivacyDialogWindow(); Text(detail) },
        properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOff),
        confirmButton = { TextButton(onClick = { if (interaction()) confirm() }, enabled = !busy) { Text("Delete", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = cancel, enabled = !busy) { Text("Cancel") } })
}
