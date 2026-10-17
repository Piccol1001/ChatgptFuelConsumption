package my.fuelledger.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import my.fuelledger.LedgerViewModel
import my.fuelledger.data.Category
import my.fuelledger.domain.Money
import my.fuelledger.domain.measurement
import my.fuelledger.receipts.ReceiptSuggestions

@Composable
fun ReceiptAttachment(ownerId: String, fileName: String?, vm: LedgerViewModel, busy: Boolean,
    change: (String?) -> Unit, categories: List<Category> = emptyList(), fuelReceipt: Boolean = true, visible: Boolean = true,
    applySuggestions: (ReceiptSuggestions) -> Unit) {
    val work by vm.receiptWork.collectAsStateWithLifecycle()
    val locked = LocalAppLocked.current
    val activity = LocalContext.current.receiptActivity()
    val latestFile by rememberUpdatedState(fileName)
    val latestWork by rememberUpdatedState(work)
    var viewing by rememberSaveable(ownerId, fileName) { mutableStateOf(false) }
    var pendingPhoto by remember(ownerId) { mutableStateOf<Uri?>(null) }
    DisposableEffect(ownerId, vm) {
        onDispose {
            // A configuration change restores this draft. Closing/cancelling it abandons only unsaved copies.
            if (activity?.isChangingConfigurations != true) {
                latestFile?.let(vm::discardReceipt)
                latestWork?.takeIf { it.ownerId == ownerId }?.let { vm.discardReceipt(it.fileName); vm.clearReceiptWork() }
            }
        }
    }
    LaunchedEffect(work, ownerId, locked) {
        if (!locked) work?.takeIf { it.ownerId == ownerId && it.suggestions == null }?.let {
            if (fileName != it.fileName) {
                change(it.fileName)
                fileName?.let(vm::discardReceipt)
            }
            vm.clearReceiptWork()
        }
    }
    LaunchedEffect(pendingPhoto, locked, busy) {
        if (!locked && !busy) pendingPhoto?.let { pendingPhoto = null; vm.importReceipt(ownerId, it) }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> pendingPhoto = uri }
    if (visible) OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Outlined.Receipt, null, tint = MaterialTheme.colorScheme.primary)
                Text(if (fuelReceipt) "Fuel receipt" else "Expense receipt", style = MaterialTheme.typography.titleMedium)
            }
            Text("Optional · choose a photo, scan it offline, and review the editable suggestions.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(enabled = !busy && !locked, onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.PhotoLibrary, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp))
                Text(if (fileName == null) "Choose receipt photo" else "Change receipt photo")
            }
            ReceiptCameraButton(enabled = !busy && !locked, onPhoto = { pendingPhoto = it }, onError = vm::showError)
            if (fileName != null) {
                ReceiptImage(fileName, vm, Modifier.fillMaxWidth().height(150.dp))
                Button(onClick = { vm.scanReceipt(ownerId, fileName) }, enabled = !busy && !locked, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.DocumentScanner, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text("Scan & review receipt")
                }
                TextButton(onClick = { viewing = true }, enabled = !busy && !locked, modifier = Modifier.fillMaxWidth()) { Text("View photo") }
                TextButton(onClick = { change(null); vm.discardReceipt(fileName); vm.clearReceiptWork() },
                    enabled = !busy && !locked, modifier = Modifier.fillMaxWidth()) { Text("Remove private attachment") }
            }
            Text("Retained copies are encrypted inside this app. You can delete the copy after extraction. Your original gallery photo is unchanged.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    work?.takeIf { it.ownerId == ownerId }?.let { item ->
        item.suggestions?.let { suggestions ->
            ReceiptReviewDialog(suggestions, categories, fuelReceipt, { vm.clearReceiptWork() }) { reviewed, deletePhoto ->
                applySuggestions(reviewed)
                if (deletePhoto) {
                    change(null)
                    vm.discardReceipt(item.fileName)
                }
                vm.clearReceiptWork()
            }
        }
    }
    if (viewing && fileName != null) Dialog(onDismissRequest = { viewing = false },
        properties = DialogProperties(usePlatformDefaultWidth = false, securePolicy = SecureFlagPolicy.SecureOff)) {
        PrivacyDialogWindow()
        Surface(Modifier.fillMaxWidth().fillMaxHeight(.92f).padding(12.dp), shape = MaterialTheme.shapes.large) {
            Column(Modifier.padding(16.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Receipt photo", style = MaterialTheme.typography.titleLarge)
                    IconButton(onClick = { viewing = false }) { Icon(Icons.Outlined.Close, "Close receipt photo") }
                }
                ReceiptImage(fileName, vm, Modifier.fillMaxWidth().weight(1f))
            }
        }
    }
}

@Composable
internal fun ReceiptImage(fileName: String, vm: LedgerViewModel, modifier: Modifier) {
    val bitmap by produceState<android.graphics.Bitmap?>(null, fileName) {
        value = withContext(Dispatchers.IO) {
            try {
                val bytes = vm.receiptBytes(fileName)
                try { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) } finally { bytes.fill(0) }
            } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { null }
        }
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        bitmap?.let { Image(it.asImageBitmap(), "Attached receipt photo", Modifier.fillMaxSize(), contentScale = ContentScale.Fit) }
            ?: Text("Receipt preview unavailable", style = MaterialTheme.typography.bodySmall)
    }
}

private data class ReviewField(val key: String, val label: String, val initial: String?, val maximum: Int = 100,
    val numeric: Boolean = false)

@Composable
internal fun ReceiptReviewDialog(suggestions: ReceiptSuggestions, categories: List<Category>, fuelReceipt: Boolean,
    dismiss: () -> Unit, apply: (ReceiptSuggestions, Boolean) -> Unit) {
    val fields = buildList {
        if (fuelReceipt) add(ReviewField("litres", "Fuel quantity (L)", suggestions.litres, 24, true))
        add(ReviewField("amount", "Total payment (RM)", suggestions.amount, 24, true))
        add(ReviewField("station", if (fuelReceipt) "Petrol station" else "Merchant / description", suggestions.station, if (fuelReceipt) 100 else 200))
        if (fuelReceipt) {
            add(ReviewField("fuelType", "Fuel type", suggestions.fuelType, 50))
            add(ReviewField("stationLocation", "Station location", suggestions.stationLocation, 200))
            add(ReviewField("odometer", "Odometer (km)", suggestions.odometer, 24, true))
        }
        add(ReviewField("date", "Date (YYYY-MM-DD)", suggestions.date, 10))
        add(ReviewField("time", "Time (HH:mm)", suggestions.time, 5))
    }
    val suggestedCategory = categories.firstOrNull { it.id == suggestions.suggestedCategoryId }?.id.orEmpty()
    var selected by rememberSaveable(suggestions, fuelReceipt) {
        mutableStateOf<List<String>>(fields.filter { !it.initial.isNullOrBlank() }.map { it.key } +
            if (!fuelReceipt && suggestedCategory.isNotBlank()) listOf("category") else emptyList())
    }
    var values by rememberSaveable(suggestions, fuelReceipt) { mutableStateOf<List<String>>(fields.map { it.initial.orEmpty().take(it.maximum) }) }
    var category by rememberSaveable(suggestions) { mutableStateOf(suggestedCategory) }
    var showText by rememberSaveable { mutableStateOf(false) }
    var deletePhoto by rememberSaveable(suggestions) { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val interaction = rememberAppLockInteraction()
    fun select(key: String, checked: Boolean) { selected = ArrayList(if (checked) (selected + key).distinct() else selected - key) }
    fun reviewedValue(key: String): String? = fields.indexOfFirst { it.key == key }.takeIf { it >= 0 && key in selected }
        ?.let { values[it].trim().ifBlank { null } }
    AlertDialog(onDismissRequest = dismiss, title = { Text("Review scanned details") },
        properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOff),
        text = {
            PrivacyDialogWindow()
            Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Check and edit these local suggestions. Only checked values replace your form. Tap Save in the form to save a transaction.")
                fields.forEachIndexed { index, field ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(field.key in selected, { select(field.key, it) }, Modifier.semantics { contentDescription = "Use ${field.label}" })
                        OutlinedTextField(values[index], { changed ->
                            if (interaction() && changed.length <= field.maximum) {
                                values = ArrayList(values).also { it[index] = changed }
                                select(field.key, changed.isNotBlank())
                            }
                        }, label = { Text(field.label) }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = if (field.numeric) KeyboardType.Decimal else KeyboardType.Text),
                            modifier = Modifier.weight(1f))
                    }
                }
                if (!fuelReceipt && categories.isNotEmpty()) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox("category" in selected, { select("category", it) }, Modifier.semantics { contentDescription = "Use suggested category" })
                    Box(Modifier.weight(1f)) {
                        Choice("Suggested category", category, listOf("" to "Choose category") + categories.map { it.id to it.name }) {
                            category = it; select("category", it.isNotBlank())
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(deletePhoto, { deletePhoto = it }, Modifier.semantics { contentDescription = "Delete private copy after extraction" })
                    Text("Delete private copy after extraction", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                }
                Text("For an existing saved attachment, deletion is completed when you save the edited transaction. The gallery original is never deleted.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = { showText = !showText }) { Text(if (showText) "Hide receipt text" else "Show receipt text") }
                if (showText) Text(suggestions.rawText.take(6000).ifBlank { "No text detected. Enter the details manually." }, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { Button(onClick = {
            if (interaction()) runCatching {
                val amount = reviewedValue("amount")?.also { Money.parse(it) }
                val litres = reviewedValue("litres")?.also { measurement(it, "Fuel quantity") }
                val odometer = reviewedValue("odometer")?.also { measurement(it, "Odometer", true) }
                val date = reviewedValue("date")?.let { LocalDate.parse(it).toString() }
                val time = reviewedValue("time")?.let { LocalTime.parse(it).withSecond(0).withNano(0).toString() }
                suggestions.copy(litres = litres, amount = amount, fuelType = reviewedValue("fuelType"),
                    station = reviewedValue("station"), stationLocation = reviewedValue("stationLocation"),
                    odometer = odometer, date = date, time = time,
                    suggestedCategoryId = category.takeIf { "category" in selected && categories.any { candidate -> candidate.id == it } })
            }.onSuccess { apply(it, deletePhoto) }.onFailure { error = "Check the selected amounts, date, and time before applying." }
        }) { Text("Apply selected") } }, dismissButton = { TextButton(onClick = dismiss) { Text("Keep manual values") } })
}

private tailrec fun Context.receiptActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.receiptActivity()
    else -> null
}
