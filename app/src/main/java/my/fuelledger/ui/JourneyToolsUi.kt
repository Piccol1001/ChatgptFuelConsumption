package my.fuelledger.ui

import android.content.ClipData
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import my.fuelledger.data.*
import my.fuelledger.domain.Money
import java.io.File

fun journeySummary(plan: JourneyPlan): String {
    val cost = journeyCost(plan)
    return listOf(plan.routeName.ifBlank { "Journey estimate" }, "${plan.origin} → ${plan.destination}",
        "Distance: ${number(cost.distanceKm)} km", "Fuel: ${Money.format(cost.fuelCents)}", "Tolls: ${Money.format(cost.tollCents)}",
        "Parking: ${Money.format(cost.parkingCents)}", "Estimated total: ${Money.format(cost.totalCents)}", "Estimate only · generated offline").joinToString("\n")
}
@Composable fun JourneyShareActions(plan: JourneyPlan) {
    val context = LocalContext.current
    var error by remember { mutableStateOf<String?>(null) }
    val text = journeySummary(plan)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton({ runCatching { context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text) }, "Export journey")) }.onFailure { error = "No compatible sharing app is available." } }) { Text("Share text") }
        TextButton({ runCatching {
            val directory = File(context.cacheDir, "local-exports").apply { mkdirs() }
            val file = File.createTempFile("journey-", ".png", directory)
            val lines = text.lines().flatMap { it.chunked(48) }
            val bitmap = Bitmap.createBitmap(1000, 100 + lines.size * 48, Bitmap.Config.ARGB_8888)
            try {
                val canvas = Canvas(bitmap); canvas.drawColor(Color.rgb(11, 18, 32))
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(243, 246, 250); textSize = 32f }
                lines.forEachIndexed { i, line -> canvas.drawText(line, 40f, 65f + i * 48, paint) }
                file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            } finally { bitmap.recycle() }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.receipt-camera", file)
            context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "image/png"; putExtra(Intent.EXTRA_STREAM, uri); clipData = ClipData.newRawUri("Journey summary", uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }, "Export journey image"))
        }.onFailure { error = "The journey image could not be shared. Try text export." } }) { Text("Share image") }
    }
    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
}
@Composable fun JourneyActualSummary(plan: JourneyPlan, expenses: List<Expense>) {
    val actual = journeyActual(plan, expenses); val cost = journeyCost(plan)
    Text("Estimated vs actual", style = MaterialTheme.typography.titleMedium)
    Text("Fuel ${Money.format(cost.fuelCents)} / ${actual.fuel?.let(Money::format) ?: "Unavailable"}", style = MaterialTheme.typography.bodySmall)
    Text("Tolls ${Money.format(cost.tollCents)} / ${actual.tolls?.let(Money::format) ?: "Unavailable"}", style = MaterialTheme.typography.bodySmall)
    Text("Parking ${Money.format(cost.parkingCents)} / ${actual.parking?.let(Money::format) ?: "Unavailable"}", style = MaterialTheme.typography.bodySmall)
}
@Composable fun JourneyActualEditor(journey: SavedJourney, expenses: List<Expense>, journeys: List<SavedJourney>, busy: Boolean, close: () -> Unit, save: (SavedJourney) -> Unit) {
    var links by remember(journey.id) { mutableStateOf(journey.plan().actualLinks) }
    var query by remember { mutableStateOf("") }
    val occupied = journeys.filter { it.id != journey.id }.flatMap { it.plan().actualLinks }.map { it.expenseId }.toSet()
    EditorShell("Link actual expenses", busy, null, close, { save(savedJourney(journey.id, journey.plan().copy(actualLinks = links), journey.timestamp)) }) {
        Text("Select existing expenses explicitly. This creates no transactions.", style = MaterialTheme.typography.bodySmall)
        TextField(query, { query = it }, "Search recorded expenses", 200)
        links.filter { link -> expenses.none { it.id == link.expenseId } }.forEach { missing -> TextButton({ links = links.filterNot { it.expenseId == missing.expenseId } }) { Text("Remove missing ${missing.kind} link") } }
        expenses.filter { it.kind == "Expense" && it.id !in occupied && (it.description.contains(query, true) || it.merchant.contains(query, true)) }.take(100).forEach { expense ->
            Text("${expense.description.ifBlank { expense.merchant.ifBlank { "Expense" } }} · ${dateTime(expense.timestamp)} · ${Money.format(expense.cents)}", style = MaterialTheme.typography.bodySmall)
            Choice("Attribution", links.find { it.expenseId == expense.id }?.kind ?: "None", listOf("None", "Fuel", "Tolls", "Parking").map { it to it }) { kind ->
                links = links.filterNot { it.expenseId == expense.id } + if (kind == "None") emptyList() else listOf(JourneyActualLink(expense.id, kind))
            }
        }
        if (expenses.size > 100) Text("Search to narrow the latest 100 matching records.", style = MaterialTheme.typography.bodySmall)
    }
}
