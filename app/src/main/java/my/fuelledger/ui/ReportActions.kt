package my.fuelledger.ui

import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import my.fuelledger.FinanceState
import my.fuelledger.LedgerState
import my.fuelledger.domain.*
import java.time.*

@Composable fun ReportActions(state: LedgerState, finance: FinanceState, start: LocalDate, end: LocalDate) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<String?>(null) }
    var pending by remember { mutableStateOf<FinancialReport?>(null) }
    var busy by remember { mutableStateOf(false) }
    val csv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        val report = pending; pending = null
        if (uri != null && report != null) scope.launch {
            busy = true
            try { withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri, "wt")?.bufferedWriter()?.use { it.write(report.csvText()) } ?: error("Document unavailable") }; message = "CSV saved" }
            catch (_: Exception) { message = "CSV could not be saved. Try another local destination." } finally { busy = false }
        }
    }
    val pdf = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        val report = pending; pending = null
        if (uri != null && report != null) scope.launch {
            busy = true
            try { withContext(Dispatchers.IO) {
                val document = PdfDocument()
                try {
                    val lines = report.lines().flatMap { it.chunked(86) }
                    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 11f; color = android.graphics.Color.BLACK }
                    lines.chunked(48).forEachIndexed { index, pageLines ->
                        val page = document.startPage(PdfDocument.PageInfo.Builder(595, 842, index + 1).create())
                        pageLines.forEachIndexed { row, line -> page.canvas.drawText(line, 32f, 42f + row * 15, paint) }
                        document.finishPage(page)
                    }
                    context.contentResolver.openOutputStream(uri, "wt")?.use { document.writeTo(it) } ?: error("Document unavailable")
                } finally { document.close() }
            }; message = "PDF saved" } catch (_: Exception) { message = "PDF could not be saved. Try another local destination." } finally { busy = false }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton({ pending = financialReport(state, finance, start, end, ZoneId.systemDefault()); csv.launch("financial-report-$start-$end.csv") }, enabled = !busy && end >= start, modifier = Modifier.weight(1f)) { Text("Export CSV") }
        OutlinedButton({ pending = financialReport(state, finance, start, end, ZoneId.systemDefault()); pdf.launch("financial-report-$start-$end.pdf") }, enabled = !busy && end >= start, modifier = Modifier.weight(1f)) { Text("Export PDF") }
    }
    message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
}
