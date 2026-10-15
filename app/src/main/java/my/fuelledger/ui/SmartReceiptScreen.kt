package my.fuelledger.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import my.fuelledger.*
import my.fuelledger.data.*
import my.fuelledger.domain.Money
import java.time.*
import java.util.UUID

@Composable
fun SmartReceiptScreen(state: LedgerState, vm: LedgerViewModel) {
    val work by vm.receiptWork.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val saved by vm.receiptSaved.collectAsStateWithLifecycle()
    val merchants by vm.receiptMerchants.collectAsStateWithLifecycle()
    val locked = LocalAppLocked.current
    var owner by rememberSaveable { mutableStateOf(UUID.randomUUID().toString()) }
    var pendingUri by rememberSaveable { mutableStateOf<String?>(null) }
    var lastAttempt by rememberSaveable { mutableStateOf<String?>(null) }
    var photo by rememberSaveable { mutableStateOf<String?>(null) }
    val item = work?.takeIf { it.ownerId == owner }
    val suggestions = item?.suggestions
    var knownScan by remember(suggestions) { mutableStateOf(false) }
    var duplicateChecked by remember(suggestions) { mutableStateOf(false) }
    val fingerprint = suggestions?.let { receiptFingerprint(it.rawText) }
    var merchant by rememberSaveable(fingerprint) { mutableStateOf(suggestions?.station.orEmpty()) }
    var amount by rememberSaveable(fingerprint) { mutableStateOf(suggestions?.amount.orEmpty()) }
    var date by rememberSaveable(fingerprint) { mutableStateOf(suggestions?.date.orEmpty()) }
    var category by rememberSaveable(fingerprint, merchants) { mutableStateOf(merchants.find { it.merchant == merchantKey(suggestions?.station.orEmpty()) }?.categoryId ?: suggestions?.suggestedCategoryId.orEmpty()) }
    var paymentMethod by rememberSaveable(fingerprint) { mutableStateOf("Not specified") }
    var keep by rememberSaveable { mutableStateOf(state.settings.keepReceiptPhotos) }
    var rememberMerchant by rememberSaveable { mutableStateOf(false) }
    var distinctPurchase by rememberSaveable(fingerprint) { mutableStateOf(false) }
    var selectedExisting by rememberSaveable(fingerprint) { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var adjust by remember { mutableStateOf(false) }
    var top by rememberSaveable { mutableStateOf("0") }
    var bottom by rememberSaveable { mutableStateOf("0") }
    val possibleDuplicate = suggestions?.let { possibleReceiptDuplicate(it, state.expenses) } ?: false
    val issues = suggestions?.let { receiptAutoSaveIssues(it, category.ifBlank { null }, knownScan || possibleDuplicate) }.orEmpty()
    fun newScan(uri: Uri) {
        vm.clearReceiptWork(); vm.clearReceiptSuccess()
        photo?.let(vm::discardReceipt); photo = null
        owner = UUID.randomUUID().toString(); pendingUri = uri.toString(); error = null
    }
    LaunchedEffect(pendingUri, locked, busy) {
        if (!locked && !busy) pendingUri?.let { pendingUri = null; vm.importReceipt(owner, Uri.parse(it)) }
    }
    LaunchedEffect(item, busy, locked) {
        if (!busy && !locked && item != null && item.suggestions == null && lastAttempt != item.fileName) {
            photo = item.fileName
            lastAttempt = item.fileName
            vm.scanReceipt(owner, item.fileName)
        }
    }
    LaunchedEffect(fingerprint) {
        if (fingerprint != null) { knownScan = vm.receiptWasScanned(fingerprint); duplicateChecked = true }
    }
    fun save(automatic: Boolean) {
        if (suggestions == null || item == null || fingerprint == null) return
        runCatching {
            val value = Expense(owner, Money.parse(amount), category,
                LocalDate.parse(date).atTime(suggestions.time?.let { runCatching { LocalTime.parse(it) }.getOrNull() } ?: LocalTime.NOON)
                    .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), description = merchant.trim(),
                payment = paymentMethod, receiptFile = if (keep) item.fileName else null)
            require(merchant.isNotBlank()) { "Enter the receipt merchant." }
            validateExpense(value)
            require(state.categories.any { it.id == category }) { "Choose a category." }
            vm.acceptReceipt(value, suggestions, fingerprint, rememberMerchant, automatic, distinctPurchase, item.fileName)
        }.onFailure { error = it.message }
    }
    LaunchedEffect(fingerprint, duplicateChecked, category, state.settings.receiptSaveMode, busy, locked) {
        if (state.settings.receiptSaveMode == "Smart" && suggestions != null && duplicateChecked && !busy && !locked && issues.isEmpty()) save(true)
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { it?.let(::newScan) }
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { ScreenHeading("Receipt Scanner", "Bundled on-device recognition. No connection or account required.", ledgerSymbol("receipt")) }
        item {
            LedgerCard {
                Text(if (state.settings.receiptSaveMode == "Smart") "Smart Auto-Save" else "Review Before Saving", style = MaterialTheme.typography.titleMedium)
                Text("Photograph one receipt at a time on a flat surface in good light. Keep the merchant, date and final total visible. For long receipts, crop unused borders or take a clearer photo; unreadable fields need review.", style = MaterialTheme.typography.bodySmall)
                OutlinedButton({ picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, enabled = !busy && !locked, modifier = Modifier.fillMaxWidth()) { Text("Choose receipt photo") }
                ReceiptCameraButton(!busy && !locked, ::newScan, vm::showError)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(keep, { keep = it }, enabled = !busy); Text("Keep encrypted private receipt photo", Modifier.weight(1f))
                }
                Text("Camera photos use a temporary private file shared with your camera app; it is deleted after import. Retained photos are encrypted. Gallery originals are unchanged.", style = MaterialTheme.typography.bodySmall)
            }
        }
        if (busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("Processing locally…") }
        saved?.let { expense -> item {
            LedgerCard(emphasized = true) {
                Text("Receipt saved successfully", style = MaterialTheme.typography.titleLarge)
                Text("${expense.description} · ${Money.format(expense.cents)}")
                Text("Your expense totals, budgets, allowance and analytics now include this transaction.")
                OutlinedButton({ vm.undoReceipt(expense) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Undo saved receipt expense") }
            }
        } }
        if (photo != null && item != null) item {
            LedgerCard {
                ReceiptImage(photo!!, vm, Modifier.fillMaxWidth().height(180.dp))
                if (suggestions == null) OutlinedButton({ vm.scanReceipt(owner, photo!!) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Retry offline scan") }
                OutlinedButton({ vm.transformReceipt(owner, photo!!, true, 0, 0) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Rotate 90° and rescan") }
                OutlinedButton({ adjust = true }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Crop top / bottom and rescan") }
            }
        }
        if (suggestions != null) item {
            LedgerCard {
                Text("Extracted receipt · ${suggestions.currency}", style = MaterialTheme.typography.titleLarge)
                issues.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                TextField(merchant, { merchant = it }, "Merchant", 200)
                NumericField(amount, { amount = it }, "Final amount (RM)")
                TextField(date, { date = it }, "Receipt date (YYYY-MM-DD)", 10)
                Choice("Expense category", category, listOf("" to "Choose category") + state.categories.map { it.id to it.name }) { category = it }
                Choice("Payment method", paymentMethod, paymentMethods.map { it to it }) { paymentMethod = it }
                Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(rememberMerchant, { rememberMerchant = it }); Text("Remember this merchant's category", Modifier.weight(1f)) }
                if (knownScan) Text("This receipt was already saved. Select its existing transaction to retain the photo without a second charge.", color = MaterialTheme.colorScheme.error)
                if (possibleDuplicate || category == FUEL_CATEGORY) {
                    Text("Check existing transactions first. Fuel receipt amounts may already be included by a refuelling entry.")
                    Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(distinctPurchase, { distinctPurchase = it }); Text("I checked: this is a different purchase", Modifier.weight(1f)) }
                }
                val matches = state.expenses.filter { expense ->
                    (suggestions.amount?.let { runCatching { Money.parse(it) }.getOrNull() } == expense.cents &&
                        suggestions.date == Instant.ofEpochMilli(expense.timestamp).atZone(ZoneId.systemDefault()).toLocalDate().toString())
                }
                if (matches.isNotEmpty() || knownScan) {
                    Choice("Existing transaction", selectedExisting, listOf("" to "Select a transaction") + (if (matches.isNotEmpty()) matches else state.expenses.take(100)).map { it.id to "${it.description} · ${Money.format(it.cents)} · ${localDate(it.timestamp)}" }) { selectedExisting = it }
                    OutlinedButton({ item?.let { vm.attachScannedReceipt(selectedExisting, it.fileName, fingerprint!!, keep) } }, enabled = !busy && selectedExisting.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Use existing transaction · no new expense") }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Button({ save(false) }, enabled = !busy && duplicateChecked && !knownScan && suggestions.currency == "MYR" && ((!possibleDuplicate && category != FUEL_CATEGORY) || distinctPurchase), modifier = Modifier.fillMaxWidth()) { Text("Save reviewed expense") }
                Text("Recognition can make mistakes. Check every field. The scanner never guesses missing dates or totals. Non-MYR receipts require manual currency conversion in Add Expense.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
    if (adjust && photo != null) EditorShell("Crop receipt", busy, error, { adjust = false }, {
        runCatching { top.toInt() to bottom.toInt() }.onSuccess { (a, b) ->
            if (a in 0..45 && b in 0..45 && a + b < 90) { vm.transformReceipt(owner, photo!!, false, a, b); adjust = false }
            else error = "Enter 0–45% for each edge, keeping at least 10% of the image."
        }.onFailure { error = "Enter whole percentages." }
    }, null) {
        NumericField(top, { top = it }, "Crop from top (%)")
        NumericField(bottom, { bottom = it }, "Crop from bottom (%)")
        Text("Keep the merchant, transaction date and total. This creates a new encrypted copy and reruns OCR.")
    }
}
