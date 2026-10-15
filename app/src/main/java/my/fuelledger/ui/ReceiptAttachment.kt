package my.fuelledger.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import my.fuelledger.LedgerViewModel
import my.fuelledger.receipts.ReceiptSuggestions

@Composable
fun ReceiptAttachment(refuelId: String, fileName: String?, vm: LedgerViewModel, busy: Boolean,
    change: (String?) -> Unit, applySuggestions: (ReceiptSuggestions) -> Unit) {
    val context = LocalContext.current
    val work by vm.receiptWork.collectAsStateWithLifecycle()
    var viewing by rememberSaveable(fileName) { mutableStateOf(false) }
    LaunchedEffect(work, refuelId) {
        work?.takeIf { it.refuelId == refuelId && it.suggestions == null }?.let {
            change(it.fileName)
            vm.clearReceiptWork()
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { vm.importReceipt(refuelId, it) }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) fileName?.let(vm::saveReceiptToGallery)
        else vm.showError("Gallery permission was not granted. Your receipt is still attached privately to this refuel.")
    }
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Outlined.Receipt, null, tint = MaterialTheme.colorScheme.primary)
                Text("Fuel receipt", style = MaterialTheme.typography.titleMedium)
            }
            Text("Optional · choose a photo from your gallery, then scan and review its details.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(enabled = !busy, onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.PhotoLibrary, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp))
                Text(if (fileName == null) "Choose receipt photo" else "Change receipt photo")
            }
            if (fileName != null) {
                ReceiptImage(fileName, vm, Modifier.fillMaxWidth().height(150.dp))
                Button(onClick = { vm.scanReceipt(refuelId, fileName) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.DocumentScanner, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text("Scan & review receipt")
                }
                Column(Modifier.fillMaxWidth()) {
                    TextButton(onClick = { viewing = true }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("View photo") }
                    TextButton(onClick = { change(null); vm.clearReceiptWork() }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Remove attachment") }
                }
                OutlinedButton(onClick = {
                    if (Build.VERSION.SDK_INT <= 28 && context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)
                        permission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    else vm.saveReceiptToGallery(fileName)
                }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Save copy to receipt album") }
                Text("Album: Fuel & Expense Tracker Receipts. The original photo stays in your gallery. Scanning runs offline and never sends your receipt to a server.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    work?.takeIf { it.refuelId == refuelId }?.suggestions?.let { suggestions ->
        ReceiptReviewDialog(suggestions, { vm.clearReceiptWork() }) {
            applySuggestions(it)
            vm.clearReceiptWork()
        }
    }
    if (viewing && fileName != null) Dialog(onDismissRequest = { viewing = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
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
private fun ReceiptImage(fileName: String, vm: LedgerViewModel, modifier: Modifier) {
    val bitmap by produceState<android.graphics.Bitmap?>(null, fileName) {
        value = withContext(Dispatchers.IO) { runCatching { BitmapFactory.decodeFile(vm.receiptFile(fileName).absolutePath) }.getOrNull() }
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        bitmap?.let { Image(it.asImageBitmap(), "Attached fuel receipt", Modifier.fillMaxSize(), contentScale = ContentScale.Fit) }
            ?: Text("Receipt preview unavailable", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun ReceiptReviewDialog(suggestions: ReceiptSuggestions, dismiss: () -> Unit, apply: (ReceiptSuggestions) -> Unit) {
    val fields = listOf("litres" to ("Fuel quantity (L)" to suggestions.litres), "amount" to ("Total payment (RM)" to suggestions.amount),
        "fuelType" to ("Fuel type" to suggestions.fuelType), "station" to ("Station" to suggestions.station),
        "stationLocation" to ("Station location" to suggestions.stationLocation), "odometer" to ("Odometer (km)" to suggestions.odometer),
        "date" to ("Date" to suggestions.date), "time" to ("Time" to suggestions.time)).filter { !it.second.second.isNullOrBlank() }
    var selected by rememberSaveable(suggestions) { mutableStateOf(ArrayList(fields.map { it.first })) }
    var showText by rememberSaveable { mutableStateOf(false) }
    AlertDialog(onDismissRequest = dismiss, title = { Text("Review scanned details") },
        text = {
            Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (fields.isEmpty()) "No reliable fields were found. Keep the photo and enter the details manually."
                    else "Check these suggestions against your receipt. Only selected values will replace the fields in your form. Nothing is saved until you tap Save.")
                fields.forEach { (key, labelValue) ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(key in selected, { checked -> selected = ArrayList(if (checked) selected + key else selected - key) })
                        Column(Modifier.weight(1f)) { Text(labelValue.first, style = MaterialTheme.typography.labelMedium); Text(labelValue.second.orEmpty()) }
                    }
                }
                TextButton(onClick = { showText = !showText }) { Text(if (showText) "Hide receipt text" else "Show receipt text") }
                if (showText) Text(suggestions.rawText.take(6000).ifBlank { "No text detected." }, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { Button(enabled = selected.isNotEmpty(), onClick = {
            fun value(key: String, data: String?) = data.takeIf { key in selected }
            apply(suggestions.copy(litres = value("litres", suggestions.litres), amount = value("amount", suggestions.amount),
                fuelType = value("fuelType", suggestions.fuelType), station = value("station", suggestions.station),
                stationLocation = value("stationLocation", suggestions.stationLocation), odometer = value("odometer", suggestions.odometer),
                date = value("date", suggestions.date), time = value("time", suggestions.time)))
        }) { Text("Apply selected") } }, dismissButton = { TextButton(onClick = dismiss) { Text("Keep manual values") } })
}
