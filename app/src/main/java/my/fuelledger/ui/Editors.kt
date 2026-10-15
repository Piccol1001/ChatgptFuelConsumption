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
    delete: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val interaction = rememberAppLockInteraction()
    Dialog(onDismissRequest = { if (!busy) dismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false, securePolicy = SecureFlagPolicy.SecureOn)) {
        PrivacyDialogWindow()
        Surface(Modifier.fillMaxWidth().widthIn(max = 560.dp).padding(12.dp).heightIn(max = 720.dp), shape = MaterialTheme.shapes.large) {
            Column(Modifier.imePadding().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
                Button(onClick = { if (interaction()) save() }, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(if (busy) "Saving…" else "Save")
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (delete != null) TextButton(onClick = { if (interaction()) delete() }, enabled = !busy, modifier = Modifier.weight(1f)) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                    TextButton(onClick = { if (interaction()) dismiss() }, enabled = !busy, modifier = Modifier.weight(1f)) { Text("Cancel") }
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
            properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn),
            text = { PrivacyDialogWindow(); TimeInput(picker) },
            confirmButton = { TextButton(onClick = { if (interaction()) { changeTime(LocalTime.of(picker.hour, picker.minute).toString()); timeOpen = false } }) { Text("Select") } },
            dismissButton = { TextButton(onClick = { timeOpen = false }) { Text("Cancel") } })
    }
}
fun timestamp(date: String, time: String) = LocalDate.parse(date).atTime(LocalTime.parse(time)).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

@Composable
fun ExpenseEditor(seed: Expense?, id: String, categories: List<Category>, busy: Boolean, vm: LedgerViewModel, dismiss: () -> Unit,
    save: (Expense) -> Unit, delete: () -> Unit) {
    val initial = remember(id) { Instant.ofEpochMilli(seed?.timestamp ?: System.currentTimeMillis()).atZone(ZoneId.systemDefault()) }
    var amount by rememberSaveable(id) { mutableStateOf(seed?.let { Money.decimal(it.cents) }.orEmpty()) }
    var category by rememberSaveable(id) { mutableStateOf(seed?.categoryId ?: categories.first().id) }
    var date by rememberSaveable(id) { mutableStateOf(initial.toLocalDate().toString()) }
    var time by rememberSaveable(id) { mutableStateOf(initial.toLocalTime().withSecond(0).withNano(0).toString()) }
    var description by rememberSaveable(id) { mutableStateOf(seed?.description.orEmpty()) }
    var notes by rememberSaveable(id) { mutableStateOf(seed?.notes.orEmpty()) }
    var payment by rememberSaveable(id) { mutableStateOf(seed?.payment ?: "Cash") }
    var receiptFile by rememberSaveable(id) { mutableStateOf(seed?.receiptFile) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    EditorShell(if (seed == null) "Add expense" else "Edit expense", busy, error, dismiss, {
        runCatching { Expense(id, Money.parse(amount), category, timestamp(date, time), description.trim(), notes.trim(), payment,
            fuelId = seed?.fuelId, receiptFile = receiptFile, recurringRuleId = seed?.recurringRuleId, recurringDate = seed?.recurringDate).also(::validateExpense) }
            .onSuccess { error = null; save(it) }.onFailure { error = it.message }
    }, if (seed != null) ({ confirmDelete = true }) else null) {
        NumericField(amount, { amount = it }, "Amount (RM)")
        Choice("Category", category, categories.map { it.id to it.name }) { category = it }
        ReceiptAttachment(id, receiptFile, vm, busy, { receiptFile = it }, categories = categories, fuelReceipt = false) { scanned ->
            scanned.amount?.let { amount = it }
            scanned.station?.let { description = it }
            scanned.date?.let { date = it }; scanned.time?.let { time = it }
            scanned.suggestedCategoryId?.takeIf { suggested -> categories.any { it.id == suggested } }?.let { category = it }
        }
        DateTimeFields(date, time, { date = it }, { time = it })
        TextField(description, { description = it }, "Description (optional)", 200)
        Choice("Payment method", payment, paymentMethods.map { it to it }) { payment = it }
        TextField(notes, { notes = it }, "Notes (optional)", 2000, true)
    }
    if (confirmDelete) DeleteConfirmation("Delete expense?", "This permanently removes this expense.", busy, { confirmDelete = false }, delete)
}

@Composable
fun RefuelEditor(seed: Refuel?, id: String, busy: Boolean, vm: LedgerViewModel, dismiss: () -> Unit, save: (Refuel) -> Unit, delete: () -> Unit) {
    val initial = remember(id) { Instant.ofEpochMilli(seed?.timestamp ?: System.currentTimeMillis()).atZone(ZoneId.systemDefault()) }
    var odometer by rememberSaveable(id) { mutableStateOf(seed?.odometer?.toString().orEmpty()) }
    var litres by rememberSaveable(id) { mutableStateOf(seed?.litres?.toString().orEmpty()) }
    var amount by rememberSaveable(id) { mutableStateOf(seed?.let { Money.decimal(it.cents) }.orEmpty()) }
    var date by rememberSaveable(id) { mutableStateOf(initial.toLocalDate().toString()) }
    var time by rememberSaveable(id) { mutableStateOf(initial.toLocalTime().withSecond(0).withNano(0).toString()) }
    var full by rememberSaveable(id) { mutableStateOf(seed?.fullTank ?: false) }
    var station by rememberSaveable(id) { mutableStateOf(seed?.station.orEmpty()) }
    var stationLocation by rememberSaveable(id) { mutableStateOf(seed?.stationLocation.orEmpty()) }
    var fuelType by rememberSaveable(id) { mutableStateOf(seed?.fuelType.orEmpty()) }
    var receiptFile by rememberSaveable(id) { mutableStateOf(seed?.receiptFile) }
    var notes by rememberSaveable(id) { mutableStateOf(seed?.notes.orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    EditorShell(if (seed == null) "Add refuel" else "Edit refuel", busy, error, dismiss, {
        runCatching { Refuel(id, timestamp(date, time), measurement(odometer, "Odometer", true), measurement(litres, "Litres"), Money.parse(amount), full, station.trim(), notes.trim(), fuelType.trim(), stationLocation.trim(), receiptFile).also(::validateRefuel) }
            .onSuccess { error = null; save(it) }.onFailure { error = it.message }
    }, if (seed != null) ({ confirmDelete = true }) else null) {
        Text("Use the odometer at every fill-up, including partial fills.", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        NumericField(odometer, { odometer = it }, "Odometer (km)")
        NumericField(litres, { litres = it }, "Fuel quantity (L)")
        NumericField(amount, { amount = it }, "Total payment (RM)")
        val price = runCatching { Money.parse(amount) / 100.0 / measurement(litres, "Litres") }.getOrNull()
        if (price != null) Text("Price per litre: RM ${number(price, 3)}/L", color = MaterialTheme.colorScheme.primary)
        val state by vm.state.collectAsStateWithLifecycle()
        val enteredTime = runCatching { timestamp(date, time) }.getOrNull()
        val previous = state.refuels.filter { it.id != id && enteredTime != null && it.timestamp < enteredTime }.maxByOrNull { it.timestamp }
        if (previous != null) {
            Text("Previous ODO: ${number(previous.odometer, 1)} km", style = MaterialTheme.typography.bodySmall)
            val estimate = runCatching {
                val point = FuelPoint(id, enteredTime!!, measurement(odometer, "Odometer", true), measurement(litres, "Litres"), Money.parse(amount), full)
                fillEstimates(listOf(previous.point(), point)).singleOrNull()
            }.getOrNull()
            if (estimate != null) OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("Per-fill estimate", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Text("${number(estimate.kmPerLitre)} km/L", style = MaterialTheme.typography.headlineSmall)
                    Text("${number(estimate.litresPer100Km)} L/100 km · RM ${number(estimate.costPerKm, 4)}/km", style = MaterialTheme.typography.bodySmall)
                    Text("Distance since last fill: ${number(estimate.distance, 1)} km", style = MaterialTheme.typography.bodySmall)
                    Text("Uses this purchase's litres. Varying tank levels can change the estimate.", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else Text("Your first fill establishes the ODO baseline. Your next fill-up will show distance and a fuel estimate.", style = MaterialTheme.typography.bodySmall)
        ReceiptAttachment(id, receiptFile, vm, busy, { receiptFile = it }) { scanned ->
            scanned.litres?.let { litres = it }; scanned.amount?.let { amount = it }
            scanned.odometer?.let { odometer = it }; scanned.fuelType?.let { fuelType = it }
            scanned.station?.let { station = it }; scanned.stationLocation?.let { stationLocation = it }
            scanned.date?.let { date = it }; scanned.time?.let { time = it }
        }
        DateTimeFields(date, time, { date = it }, { time = it })
        val types = listOf("" to "Not specified", "RON95" to "RON95", "RON97" to "RON97", "Diesel" to "Diesel", "Diesel Euro 5" to "Diesel Euro 5", "Other" to "Other")
        Choice("Fuel type (optional)", fuelType, if (fuelType.isNotBlank() && types.none { it.first == fuelType }) types + (fuelType to fuelType) else types) { fuelType = it }
        TextField(station, { station = it }, "Petrol station (optional)", 100)
        TextField(stationLocation, { stationLocation = it }, "Station location (optional)", 200)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Full tank? (optional)", modifier = Modifier.weight(1f))
            Switch(full, { full = it })
        }
        Text("Mark full tanks only when applicable to keep the additional full-tank measurement available. Per-fill estimates work with every fill-up.", style = MaterialTheme.typography.bodySmall)
        TextField(notes, { notes = it }, "Notes (optional)", 2000, true)
        Text("Payment is automatically included once in Fuel & Transport expenses.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
    }
    if (confirmDelete) DeleteConfirmation("Delete refuel?", "This removes the refuel and its linked expense. Measured consumption will be recalculated.", busy, { confirmDelete = false }, delete)
}
@Composable
fun DeleteConfirmation(title: String, detail: String, busy: Boolean, cancel: () -> Unit, confirm: () -> Unit) {
    val interaction = rememberAppLockInteraction()
    AlertDialog(onDismissRequest = { if (!busy) cancel() }, title = { Text(title) }, text = { PrivacyDialogWindow(); Text(detail) },
        properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn),
        confirmButton = { TextButton(onClick = { if (interaction()) confirm() }, enabled = !busy) { Text("Delete", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = cancel, enabled = !busy) { Text("Cancel") } })
}
