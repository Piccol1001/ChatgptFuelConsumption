package my.fuelledger.ui

import android.app.TimePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import my.fuelledger.data.*
import my.fuelledger.domain.*
import java.time.*

@Composable
fun EditorShell(title: String, busy: Boolean, error: String?, dismiss: () -> Unit, save: () -> Unit,
    delete: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = { if (!busy) dismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth().widthIn(max = 560.dp).padding(12.dp).heightIn(max = 720.dp), shape = MaterialTheme.shapes.large) {
            Column(Modifier.imePadding().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (delete != null) TextButton(onClick = delete, enabled = !busy) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = dismiss, enabled = !busy) { Text("Cancel") }
                    Button(onClick = save, enabled = !busy) { Text(if (busy) "Saving…" else "Save") }
                }
            }
        }
    }
}
@Composable
fun NumericField(value: String, change: (String) -> Unit, label: String) {
    OutlinedTextField(value, { if (it.length <= 24) change(it) }, label = { Text(label) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
}
@Composable
fun TextField(value: String, change: (String) -> Unit, label: String, max: Int, multiline: Boolean = false) {
    OutlinedTextField(value, { if (it.length <= max) change(it) }, label = { Text(label) }, singleLine = !multiline,
        minLines = if (multiline) 2 else 1, maxLines = if (multiline) 4 else 1, modifier = Modifier.fillMaxWidth())
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Choice(label: String, selected: String, values: List<Pair<String, String>>, change: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = open, onExpandedChange = { open = !open }) {
        OutlinedTextField(values.firstOrNull { it.first == selected }?.second ?: selected, {}, readOnly = true,
            label = { Text(label) }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(open) },
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable))
        ExposedDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            values.forEach { (id, name) -> DropdownMenuItem(text = { Text(name) }, onClick = { change(id); open = false }) }
        }
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateButton(date: LocalDate, label: String, change: (LocalDate) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { open = true }, modifier = modifier) { Text("$label: ${dateLabel(date)}") }
    if (open) {
        val picker = rememberDatePickerState(initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(onDismissRequest = { open = false },
            confirmButton = { TextButton(onClick = {
                picker.selectedDateMillis?.let { change(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                open = false
            }) { Text("Select") } }, dismissButton = { TextButton(onClick = { open = false }) { Text("Cancel") } }) {
            DatePicker(picker)
        }
    }
}
@Composable
fun DateTimeFields(date: String, time: String, changeDate: (String) -> Unit, changeTime: (String) -> Unit) {
    val context = LocalContext.current
    DateButton(LocalDate.parse(date), "Date", { changeDate(it.toString()) }, Modifier.fillMaxWidth())
    OutlinedButton(onClick = {
        val selected = LocalTime.parse(time)
        TimePickerDialog(context, { _, hour, minute -> changeTime(LocalTime.of(hour, minute).toString()) }, selected.hour, selected.minute, false).show()
    }, modifier = Modifier.fillMaxWidth()) { Text("Time: $time · device local time") }
}
fun timestamp(date: String, time: String) = LocalDate.parse(date).atTime(LocalTime.parse(time)).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

@Composable
fun ExpenseEditor(seed: Expense?, id: String, categories: List<Category>, busy: Boolean, dismiss: () -> Unit,
    save: (Expense) -> Unit, delete: () -> Unit) {
    val initial = remember(id) { Instant.ofEpochMilli(seed?.timestamp ?: System.currentTimeMillis()).atZone(ZoneId.systemDefault()) }
    var amount by rememberSaveable(id) { mutableStateOf(seed?.let { Money.decimal(it.cents) }.orEmpty()) }
    var category by rememberSaveable(id) { mutableStateOf(seed?.categoryId ?: categories.first().id) }
    var date by rememberSaveable(id) { mutableStateOf(initial.toLocalDate().toString()) }
    var time by rememberSaveable(id) { mutableStateOf(initial.toLocalTime().withSecond(0).withNano(0).toString()) }
    var description by rememberSaveable(id) { mutableStateOf(seed?.description.orEmpty()) }
    var notes by rememberSaveable(id) { mutableStateOf(seed?.notes.orEmpty()) }
    var payment by rememberSaveable(id) { mutableStateOf(seed?.payment ?: "Cash") }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    EditorShell(if (seed == null) "Add expense" else "Edit expense", busy, error, dismiss, {
        runCatching { Expense(id, Money.parse(amount), category, timestamp(date, time), description.trim(), notes.trim(), payment).also(::validateExpense) }
            .onSuccess { error = null; save(it) }.onFailure { error = it.message }
    }, if (seed != null) ({ confirmDelete = true }) else null) {
        NumericField(amount, { amount = it }, "Amount (RM)")
        Choice("Category", category, categories.map { it.id to it.name }) { category = it }
        DateTimeFields(date, time, { date = it }, { time = it })
        TextField(description, { description = it }, "Description (optional)", 200)
        Choice("Payment method", payment, paymentMethods.map { it to it }) { payment = it }
        TextField(notes, { notes = it }, "Notes (optional)", 2000, true)
    }
    if (confirmDelete) DeleteConfirmation("Delete expense?", "This permanently removes this expense.", busy, { confirmDelete = false }, delete)
}

@Composable
fun RefuelEditor(seed: Refuel?, id: String, busy: Boolean, dismiss: () -> Unit, save: (Refuel) -> Unit, delete: () -> Unit) {
    val initial = remember(id) { Instant.ofEpochMilli(seed?.timestamp ?: System.currentTimeMillis()).atZone(ZoneId.systemDefault()) }
    var odometer by rememberSaveable(id) { mutableStateOf(seed?.odometer?.toString().orEmpty()) }
    var litres by rememberSaveable(id) { mutableStateOf(seed?.litres?.toString().orEmpty()) }
    var amount by rememberSaveable(id) { mutableStateOf(seed?.let { Money.decimal(it.cents) }.orEmpty()) }
    var date by rememberSaveable(id) { mutableStateOf(initial.toLocalDate().toString()) }
    var time by rememberSaveable(id) { mutableStateOf(initial.toLocalTime().withSecond(0).withNano(0).toString()) }
    var full by rememberSaveable(id) { mutableStateOf(seed?.fullTank ?: true) }
    var station by rememberSaveable(id) { mutableStateOf(seed?.station.orEmpty()) }
    var notes by rememberSaveable(id) { mutableStateOf(seed?.notes.orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    EditorShell(if (seed == null) "Add refuel" else "Edit refuel", busy, error, dismiss, {
        runCatching { Refuel(id, timestamp(date, time), measurement(odometer, "Odometer", true), measurement(litres, "Litres"), Money.parse(amount), full, station.trim(), notes.trim()).also(::validateRefuel) }
            .onSuccess { error = null; save(it) }.onFailure { error = it.message }
    }, if (seed != null) ({ confirmDelete = true }) else null) {
        DateTimeFields(date, time, { date = it }, { time = it })
        NumericField(odometer, { odometer = it }, "Odometer (km)")
        NumericField(litres, { litres = it }, "Fuel quantity (L)")
        NumericField(amount, { amount = it }, "Total payment (RM)")
        val price = runCatching { Money.parse(amount) / 100.0 / measurement(litres, "Litres") }.getOrNull()
        if (price != null) Text("Price per litre: RM ${number(price, 3)}/L", color = MaterialTheme.colorScheme.primary)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Filled to a full tank?"); Switch(full, { full = it }) }
        Text("A full tank starts or completes a measured cycle. Partial refuels count toward the next full tank.", style = MaterialTheme.typography.bodySmall)
        TextField(station, { station = it }, "Petrol station (optional)", 100)
        TextField(notes, { notes = it }, "Notes (optional)", 2000, true)
        Text("Payment is automatically included once in Fuel & Transport expenses.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
    }
    if (confirmDelete) DeleteConfirmation("Delete refuel?", "This removes the refuel and its linked expense. Measured consumption will be recalculated.", busy, { confirmDelete = false }, delete)
}
@Composable
fun DeleteConfirmation(title: String, detail: String, busy: Boolean, cancel: () -> Unit, confirm: () -> Unit) {
    AlertDialog(onDismissRequest = { if (!busy) cancel() }, title = { Text(title) }, text = { Text(detail) },
        confirmButton = { TextButton(onClick = confirm, enabled = !busy) { Text("Delete", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = cancel, enabled = !busy) { Text("Cancel") } })
}
