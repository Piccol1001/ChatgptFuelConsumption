package my.fuelledger.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import kotlinx.coroutines.launch
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import my.fuelledger.JourneyViewModel
import my.fuelledger.data.*
import my.fuelledger.domain.*
import java.util.UUID

@Serializable
private data class FeeInput(val id: String = UUID.randomUUID().toString(), val name: String = "Toll", val amount: String = "0.00", val notes: String = "") {
    fun fee() = JourneyFee(name.trim(), zeroMoney(amount), notes.trim())
}
private fun zeroMoney(text: String): Long {
    require(Regex("\\d{1,10}(\\.\\d{1,2})?").matches(text.trim())) { "Enter fees with at most two decimal places; use 0 for none." }
    return text.trim().toBigDecimal().movePointRight(2).longValueExact().also { require(it in 0..MAX_CENTS) }
}
@Serializable
private data class JourneyInput(val origin: String = "", val destination: String = "", val outbound: String = "",
    val roundTrip: Boolean = false, val returnDistance: String = "", val consumption: String = "", val price: String = "2.05",
    val outboundTolls: List<FeeInput> = emptyList(), val returnTolls: List<FeeInput> = emptyList(),
    val parking: String = "0.00", val others: List<FeeInput> = emptyList(), val people: String = "1",
    val shareFuel: Boolean = true, val shareTolls: Boolean = true, val shareParking: Boolean = true, val shareOther: Boolean = true,
    val consumptionOverride: Boolean = false, val routeName: String = "", val actualLinks: List<JourneyActualLink> = emptyList()) {
    fun plan(effectiveConsumption: String, source: String) = JourneyPlan(origin.trim(), destination.trim(), measurement(outbound, "Outbound distance"), roundTrip,
        measurement(if (roundTrip) returnDistance else outbound, "Return distance", allowZero = true), measurement(effectiveConsumption, "Consumption"), price.trim(),
        outboundTolls.map { it.fee() }, returnTolls.map { it.fee() }, zeroMoney(parking), others.map { it.fee() },
        people.toIntOrNull() ?: 1, shareFuel, shareTolls, shareParking, shareOther, costVersion = 2, consumptionSource = source, routeName = routeName, actualLinks = actualLinks)
}
private fun journeyInput(plan: JourneyPlan) = JourneyInput(plan.origin, plan.destination, plan.outboundKm.toString(), plan.roundTrip,
    plan.returnKm.toString(), if (plan.consumptionSource == "History") "" else plan.consumption.toString(), plan.petrolPrice,
    plan.outboundTolls.map { FeeInput(name = it.name, amount = Money.decimal(it.cents), notes = it.notes) },
    plan.returnTolls.map { FeeInput(name = it.name, amount = Money.decimal(it.cents), notes = it.notes) }, Money.decimal(plan.parkingCents),
    plan.otherFees.map { FeeInput(name = it.name, amount = Money.decimal(it.cents), notes = it.notes) }, plan.people.toString(),
    plan.shareFuel, plan.shareTolls, plan.shareParking, plan.shareOther, consumptionOverride = plan.consumptionSource in setOf("Legacy", "Override"), routeName = plan.routeName, actualLinks = plan.actualLinks)

@Composable
fun JourneyScreen(settings: AppSettings, vm: JourneyViewModel, refuels: List<Refuel> = emptyList(), saveFallback: ((Double) -> Unit)? = null, openSettings: (() -> Unit)? = null, expenses: List<Expense> = emptyList()) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val journeys by vm.journeys.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val operationError by vm.error.collectAsStateWithLifecycle()
    var id by rememberSaveable { mutableStateOf(UUID.randomUUID().toString()) }
    var encoded by rememberSaveable { mutableStateOf(backupJson.encodeToString(JourneyInput(price = settings.petrolPrice))) }
    val input = backupJson.decodeFromString<JourneyInput>(encoded)
    fun update(value: JourneyInput) { encoded = backupJson.encodeToString(value) }
    var error by remember { mutableStateOf<String?>(null) }
    var notice by rememberSaveable { mutableStateOf<String?>(null) }
    var actualEditor by remember { mutableStateOf<SavedJourney?>(null) }
    var deleting by remember { mutableStateOf<SavedJourney?>(null) }
    val saved = journeys.find { it.id == id }
    val historical = remember(refuels) { historicalConsumption(refuels.map { it.point() }) }
    val fallback = input.consumption.ifBlank { if (settings.consumptionFallbackEnabled) settings.consumption.toString() else "" }
    val effectiveConsumption = if (input.consumptionOverride) input.consumption else historical?.toString() ?: fallback
    val consumptionSource = if (input.consumptionOverride) "Override" else if (historical != null) "History" else "Fallback"
    val estimate = runCatching { journeyCost(input.plan(effectiveConsumption, consumptionSource)) }
    val locations = journeys.flatMap { listOf(it.plan().origin, it.plan().destination) }.distinct()
    LazyColumn(state = listState, contentPadding = ledgerScreenPadding, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { ScreenHeading("Journey", "Offline journey estimates", ledgerSymbol("route"), openSettings) }
        item {
            LedgerCard {
                Text(if (saved != null) "Edit saved journey" else "Plan a journey", style = MaterialTheme.typography.titleMedium)
                TextField(input.routeName, { update(input.copy(routeName = it)) }, "Route name (optional)", 100)
                TextField(input.origin, { update(input.copy(origin = it)) }, "Origin", 200)
                LocationSuggestions(input.origin, locations) { update(input.copy(origin = it)) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    IconButton({ update(input.copy(origin = input.destination, destination = input.origin,
                        outbound = if (input.roundTrip) input.returnDistance else input.outbound,
                        returnDistance = if (input.roundTrip) input.outbound else input.returnDistance,
                        outboundTolls = input.returnTolls, returnTolls = input.outboundTolls)) }) {
                        androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Outlined.SwapVert, "Reverse journey")
                    }
                }
                TextField(input.destination, { update(input.copy(destination = it)) }, "Destination", 200)
                LocationSuggestions(input.destination, locations) { update(input.copy(destination = it)) }
                Text("Enter distance manually or load a saved route.", style = MaterialTheme.typography.bodySmall)
                NumericField(input.outbound, { update(input.copy(outbound = it)) }, "Outbound distance (km)")
                JourneyTypeToggle(input.roundTrip) { round ->
                    update(input.copy(roundTrip = round, returnDistance = input.returnDistance.ifBlank { input.outbound }))
                }
                if (input.roundTrip) {
                    NumericField(input.returnDistance, { update(input.copy(returnDistance = it)) }, "Return distance (km)")
                    Text("Return distance and tolls are independent.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item {
            LedgerCard {
                Text("Fuel settings", style = MaterialTheme.typography.titleMedium)
                Text("Historical consumption", style = MaterialTheme.typography.labelLarge)
                Text(historical?.let { "${number(it)} L/100 km" } ?: "Not enough fuel data", style = MaterialTheme.typography.titleLarge)
                if (historical != null || input.consumptionOverride) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Override this journey", Modifier.weight(1f))
                        Switch(input.consumptionOverride, { enabled -> update(input.copy(consumptionOverride = enabled, consumption = if (enabled) effectiveConsumption else "")) })
                    }
                }
                if (historical == null || input.consumptionOverride) {
                    NumericField(if (input.consumptionOverride) input.consumption else fallback, { update(input.copy(consumption = it)) },
                        if (input.consumptionOverride) "Override (L/100 km)" else "Fallback (L/100 km)")
                    if (historical == null && saveFallback != null) TextButton({
                        runCatching { measurement(if (input.consumptionOverride) input.consumption else fallback, "Fallback consumption").also { require(it <= 1000) } }
                            .onSuccess(saveFallback).onFailure { error = "Enter a fallback between 0 and 1000 L/100 km." }
                    }, enabled = !busy) { Text("Save fallback for new journeys") }
                }
                NumericField(input.price, { update(input.copy(price = it)) }, "Petrol price (RM/L)")
                ExpandableHelp("Compare petrol prices") {
                    var alternative by rememberSaveable { mutableStateOf("") }
                    NumericField(alternative, { alternative = it }, "Scenario price (RM/L)")
                    val scenario = runCatching { journeyCost(input.plan(effectiveConsumption, consumptionSource).copy(petrolPrice = alternative)) }.getOrNull()
                    Text(scenario?.let { "Scenario fuel ${Money.format(it.fuelCents)} · total ${Money.format(it.totalCents)}" } ?: "Enter a valid price for comparison", style = MaterialTheme.typography.bodyMedium)
                }
                ExpandableHelp("Fuel estimate") { Text("Uses the distance-weighted full-tank average. Overrides affect only this journey. An enabled fallback is used only while history is insufficient.", style = MaterialTheme.typography.bodySmall) }
            }
        }
        item { FeeSection("Outbound toll fees", input.outboundTolls, "Toll", { update(input.copy(outboundTolls = it)) }) }
        if (input.roundTrip) item { FeeSection("Return toll fees", input.returnTolls, "Toll", { update(input.copy(returnTolls = it)) }) }
        item {
            LedgerCard {
                Text("Parking", style = MaterialTheme.typography.titleMedium)
                NumericField(input.parking, { update(input.copy(parking = it)) }, "Parking (RM)")
                Text("Journey total; not doubled for return travel.", style = MaterialTheme.typography.bodySmall)
            }
        }
        item {
            LedgerCard(emphasized = true) {
                Text("Total estimated journey cost", style = MaterialTheme.typography.labelLarge)
                estimate.getOrNull()?.let { cost ->
                    JourneyCostSummary(cost)
                } ?: Text(estimate.exceptionOrNull()?.message ?: "Check the journey inputs.", color = MaterialTheme.colorScheme.error)
                if (saved?.plan()?.costVersion == 1) Text("Saving uses fuel + tolls + parking; legacy fields are retained.", style = MaterialTheme.typography.bodySmall)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                notice?.let { Text(it) }
                Button({
                    runCatching { savedJourney(id, input.plan(effectiveConsumption, consumptionSource)) }.onSuccess {
                        vm.save(it) { error = null; notice = "Journey saved locally." }
                    }.onFailure { error = it.message }
                }, enabled = !busy && estimate.isSuccess, modifier = Modifier.fillMaxWidth()) { Text(if (saved != null) "Update saved journey" else "Save journey") }
                OutlinedButton({ id = UUID.randomUUID().toString(); update(JourneyInput(price = settings.petrolPrice)); error = null; notice = null }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Start new journey") }
            }
        }
        item { Text("Saved journeys", style = MaterialTheme.typography.titleLarge) }
        if (journeys.isEmpty()) item { EmptyState("No saved journeys", "Save a plan to reuse its locations, distance and fees offline.") }
        items(journeys, key = { it.id }) { journey ->
            val plan = journey.plan()
            LedgerCard {
                Text(plan.routeName.ifBlank { "${plan.origin} → ${plan.destination}" }, style = MaterialTheme.typography.titleMedium)
                Text("${dateTime(journey.timestamp)} · ${if (plan.roundTrip) "Round-trip" else "One-way"}", style = MaterialTheme.typography.bodySmall)
                Text(Money.format(journeyCost(plan).totalCents), style = MaterialTheme.typography.titleLarge)
                if (plan.costVersion == 1 && plan.otherFees.any { it.cents > 0 }) Text("Historical total includes legacy fees ${Money.format(plan.otherFees.sumOf { it.cents })}", style = MaterialTheme.typography.bodySmall)
                Button({ id = journey.id; update(journeyInput(plan)); notice = "Saved journey loaded."; error = null; scope.launch { listState.scrollToItem(0) } }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Edit / load route") }
                OutlinedButton({ id = UUID.randomUUID().toString(); update(journeyInput(plan.copy(petrolPrice = settings.petrolPrice)).copy(consumptionOverride = false, consumption = "", actualLinks = emptyList())); notice = "Duplicate loaded. Review fees and save as a new journey."; error = null; scope.launch { listState.scrollToItem(0) } }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Use as preset · current fuel settings") }
                JourneyActualSummary(plan, expenses)
                TextButton({ actualEditor = journey }) { Text("Link recorded actual costs") }
                JourneyShareActions(plan)
                TextButton({ deleting = journey }, enabled = !busy) { Text("Delete journey") }
            }
        }
    }
    actualEditor?.let { journey -> JourneyActualEditor(journey, expenses, journeys, busy, { actualEditor = null }) { updated -> vm.save(updated) { actualEditor = null; if (id == updated.id) update(input.copy(actualLinks = updated.plan().actualLinks)) } } }
    deleting?.let { journey -> DeleteConfirmation("Delete saved journey?", "Only this plan is removed. Your actual expenses and refuelling records stay available.", busy, { deleting = null }) {
        vm.delete(journey.id) { deleting = null; if (id == journey.id) id = UUID.randomUUID().toString() }
    } }
    operationError?.let { message -> AlertDialog(onDismissRequest = vm::dismissError, title = { Text("Unable to update journey") }, text = { PrivacyDialogWindow(); Text(message) }, confirmButton = { TextButton(vm::dismissError) { Text("OK") } }) }
}

@Composable
private fun LocationSuggestions(value: String, locations: List<String>, choose: (String) -> Unit) {
    val suggestions = locations.filter { it.startsWith(value, ignoreCase = true) && it != value }.take(6)
    // An empty Row still adds a slot to LedgerCard's spaced Column. Emit nothing instead.
    if (suggestions.isEmpty()) return
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        suggestions.forEach {
            SuggestionChip(onClick = { choose(it) }, label = { Text(it) }, modifier = Modifier.heightIn(min = 48.dp))
        }
    }
}
@Composable
private fun FeeSection(title: String, entries: List<FeeInput>, defaultName: String, update: (List<FeeInput>) -> Unit) {
    LedgerCard {
        Text(title, style = MaterialTheme.typography.titleMedium)
        ExpandableHelp("Toll entries") { Text("Enter one total or named segments. Return tolls are entered separately.", style = MaterialTheme.typography.bodySmall) }
        entries.forEach { entry -> key(entry.id) {
            TextField(entry.name, { value -> update(entries.map { if (it.id == entry.id) it.copy(name = value) else it }) }, "$defaultName name", 100)
            NumericField(entry.amount, { value -> update(entries.map { if (it.id == entry.id) it.copy(amount = value) else it }) }, "Amount (RM)")
            TextField(entry.notes, { value -> update(entries.map { if (it.id == entry.id) it.copy(notes = value) else it }) }, "Notes (optional)", 1000)
            TextButton({ update(entries.filterNot { it.id == entry.id }) }) { Text("Remove entry") }
        } }
        val total = runCatching { entries.sumOf { zeroMoney(it.amount) } }.getOrNull()
        Text("Subtotal: ${total?.let(Money::format) ?: "Check amounts"}")
        OutlinedButton({ if (entries.size < 100) update(entries + FeeInput(name = defaultName)) }, modifier = Modifier.fillMaxWidth()) { Text("Add $defaultName entry") }
    }
}

@Composable
internal fun JourneyCostSummary(cost: JourneyCost) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(Money.format(cost.totalCents), style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.primary)
                    Text("${number(cost.distanceKm)} km total · Fuel ${Money.format(cost.fuelCents)}")
                    Text("Tolls ${Money.format(cost.tollCents)} · Parking ${Money.format(cost.parkingCents)}")
                    Text("Estimate only", style = MaterialTheme.typography.labelSmall)
    }
}
