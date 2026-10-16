package my.fuelledger.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import my.fuelledger.LedgerState
import my.fuelledger.domain.*

@Composable fun VehicleAnalysisCard(state: LedgerState) {
    val change = consumptionChange(state.refuels.map { it.point() })
    val coverage = vehicleCoverage(state.refuels, state.expenses, state.categories)
    LedgerCard {
        Text("Fuel performance & recorded costs", style = MaterialTheme.typography.titleMedium)
        Text(change?.let { "Recent consumption ${if (it.percent >= 0) "increased" else "improved"} ${number(kotlin.math.abs(it.percent), 1)}%" }
            ?: "Consumption comparison needs four valid full-tank intervals.", style = MaterialTheme.typography.bodyMedium)
        if (change != null) Text("Latest two intervals vs earlier intervals. Driving conditions affect consumption.", style = MaterialTheme.typography.bodySmall)
        Text(averageFuelPrice(state.refuels)?.let { "Average price paid: RM ${number(it, 3)}/L · volume weighted" } ?: "No recorded fuel prices", style = MaterialTheme.typography.bodyMedium)
        coverage?.let {
            Text("Fuel-only: RM ${number(it.fuelRmPerKm, 4)}/km", style = MaterialTheme.typography.titleMedium)
            Text("Recorded operating: RM ${number(it.recordedRmPerKm, 4)}/km", style = MaterialTheme.typography.titleMedium)
            Text("${dateLabel(localDate(it.start))} – ${dateLabel(localDate(it.end))} · ${number(it.distance)} measured km", style = MaterialTheme.typography.bodySmall)
            Text("Includes fuel and recorded ${state.categories.filter { c -> c.id in it.categoryIds }.joinToString { c -> c.name }}. Complete ownership cost is unavailable.", style = MaterialTheme.typography.bodySmall)
        } ?: Text("Cost per kilometre needs a complete full-tank interval.", style = MaterialTheme.typography.bodySmall)
        val recent = state.refuels.sortedBy { it.timestamp }.takeLast(2)
        if (recent.size == 2) Text("Last price change: RM ${number(recent[1].pricePerLitre - recent[0].pricePerLitre, 3)}/L. Price changes and consumption changes are measured separately.", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable fun FuelPriceHistory(refuels: List<my.fuelledger.data.Refuel>) {
    LedgerCard( androidx.compose.ui.Modifier.chartGestureRegion()) {
        Text("Price history · RM/L", style = MaterialTheme.typography.titleMedium)
        refuels.sortedByDescending { it.timestamp }.take(8).forEach {
            Text("${dateLabel(localDate(it.timestamp))} · RM ${number(it.pricePerLitre, 3)}/L · ${it.fuelType.ifBlank { "Fuel" }}", style = MaterialTheme.typography.bodyMedium)
        }
        if (refuels.isEmpty()) Text("No price history yet", style = MaterialTheme.typography.bodySmall)
    }
}
