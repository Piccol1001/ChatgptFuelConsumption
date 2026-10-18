package my.fuelledger.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import my.fuelledger.LedgerState
import my.fuelledger.LedgerViewModel
import java.time.*

@Composable fun LocalDataSettings(state: LedgerState, vm: LedgerViewModel, busy: Boolean) {
    val context = LocalContext.current
    var bytes by remember { mutableLongStateOf(0L) }
    var confirmation by remember { mutableStateOf("") }
    var start by remember { mutableStateOf(LocalDate.now().withDayOfMonth(1)) }
    var end by remember { mutableStateOf(LocalDate.now()) }
    val history by vm.undoHistory.collectAsStateWithLifecycle()
    LaunchedEffect(busy) {
        if (!busy) bytes = withContext(Dispatchers.IO) {
            listOf(context.filesDir, context.noBackupFilesDir, context.getDatabasePath("fuel-ledger.db").parentFile).filterNotNull().distinct().sumOf { directory -> directory.walkTopDown().filter { it.isFile }.sumOf { it.length() } }
        }
    }
    LedgerCard {
        Text("Local data", style = MaterialTheme.typography.titleMedium)
        Text("Private files & database: ${number(bytes / 1048576.0, 1)} MB", style = MaterialTheme.typography.bodySmall)
        TextButton({ confirmation = "images" }, enabled = !busy) { Text("Delete stored receipt images") }
        if (history.isNotEmpty()) TextButton({ confirmation = "undo" }, enabled = !busy) { Text("Clear Undo history (${history.size})") }
        ExpandableHelp("Delete historical transactions") {
            DateButton(start, "From", { start = it }, Modifier.fillMaxWidth())
            DateButton(end, "To", { end = it }, Modifier.fillMaxWidth())
            Text("${state.expenses.count { localDate(it.timestamp) in start..end }} transactions in this period", style = MaterialTheme.typography.bodySmall)
            TextButton({ confirmation = "history" }, enabled = !busy && end >= start) { Text("Delete selected period", color = MaterialTheme.colorScheme.error) }
        }
    }
    if (confirmation.isNotEmpty()) DeleteConfirmation(
        when (confirmation) { "images" -> "Delete receipt images?"; "undo" -> "Clear Undo history?"; else -> "Delete selected historical transactions?" },
        when (confirmation) { "images" -> "All retained receipt photos and pending scans will be removed. Transactions are kept."; "undo" -> "Old transaction versions and their unused images will be removed. Current records are kept."; else -> "$start to $end: expenses and associated refuels will be removed. Income, plans and preferences are kept. Make an encrypted backup before proceeding. Undo is available from Expenses while its history is retained." }, busy, { confirmation = "" }) {
        val action = confirmation; confirmation = ""
        when (action) { "images" -> vm.deleteReceiptImages(); "undo" -> vm.clearUndoHistory(); else -> vm.deleteHistorical(start, end) }
    }
}
