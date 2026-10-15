package my.fuelledger.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import kotlinx.coroutines.launch
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
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
    val roundTrip: Boolean = false, val returnDistance: String = "", val consumption: String = "7.0", val price: String = "2.05",
    val outboundTolls: List<FeeInput> = emptyList(), val returnTolls: List<FeeInput> = emptyList(),
    val parking: String = "0.00", val others: List<FeeInput> = emptyList(), val people: String = "1",
    val shareFuel: Boolean = true, val shareTolls: Boolean = true, val shareParking: Boolean = true, val shareOther: Boolean = true) {
    fun plan() = JourneyPlan(origin.trim(), destination.trim(), measurement(outbound, "Outbound distance"), roundTrip,
        measurement(if (roundTrip) returnDistance else outbound, "Return distance", allowZero = true), measurement(consumption, "Consumption"), price.trim(),
        outboundTolls.map { it.fee() }, returnTolls.map { it.fee() }, zeroMoney(parking), others.map { it.fee() },
        people.toIntOrNull() ?: throw IllegalArgumentException("Enter the number of people."), shareFuel, shareTolls, shareParking, shareOther)
}
private fun journeyInput(plan: JourneyPlan) = JourneyInput(plan.origin, plan.destination, plan.outboundKm.toString(), plan.roundTrip,
    plan.returnKm.toString(), plan.consumption.toString(), plan.petrolPrice,
    plan.outboundTolls.map { FeeInput(name = it.name, amount = Money.decimal(it.cents), notes = it.notes) },
    plan.returnTolls.map { FeeInput(name = it.name, amount = Money.decimal(it.cents), notes = it.notes) }, Money.decimal(plan.parkingCents),
    plan.otherFees.map { FeeInput(name = it.name, amount = Money.decimal(it.cents), notes = it.notes) }, plan.people.toString(),
    plan.shareFuel, plan.shareTolls, plan.shareParking, plan.shareOther)

@Composable
fun JourneyScreen(settings: AppSettings, vm: JourneyViewModel) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val journeys by vm.journeys.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val operationError by vm.error.collectAsStateWithLifecycle()
    var id by rememberSaveable { mutableStateOf(UUID.randomUUID().toString()) }
    var encoded by rememberSaveable { mutableStateOf(backupJson.encodeToString(JourneyInput(consumption = settings.consumption.toString(), price = settings.petrolPrice))) }
    val input = backupJson.decodeFromString<JourneyInput>(encoded)
    fun update(value: JourneyInput) { encoded = backupJson.encodeToString(value) }
    var error by remember { mutableStateOf<String?>(null) }
    var notice by rememberSaveable { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf<SavedJourney?>(null) }
    val saved = journeys.find { it.id == id }
    val estimate = runCatching { journeyCost(input.plan()) }
    val locations = journeys.flatMap { listOf(it.plan().origin, it.plan().destination) }.distinct().sorted()
    LazyColumn(state = listState, contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { ScreenHeading("Journey Planner", "Offline estimates for your next journey. Your fuel records stay separate.", ledgerSymbol("route")) }
        item {
            LedgerCard {
                Text(if (saved != null) "Edit saved journey" else "Plan a journey", style = MaterialTheme.typography.titleLarge)
                TextField(input.origin, { update(input.copy(origin = it)) }, "Origin", 200)
                LocationSuggestions(input.origin, locations) { update(input.copy(origin = it)) }
                TextField(input.destination, { update(input.copy(destination = it)) }, "Destination", 200)
                LocationSuggestions(input.destination, locations) { update(input.copy(destination = it)) }
                Text("Enter driving distance manually or load a saved journey. Place names do not calculate a route.", style = MaterialTheme.typography.bodySmall)
                NumericField(input.outbound, { update(input.copy(outbound = it)) }, "One-way / outbound distance (km)")
                Choice("Journey type", if (input.roundTrip) "round" else "one", listOf("one" to "One-way", "round" to "Round-trip")) {
                    update(input.copy(roundTrip = it == "round", returnDistance = if (it == "round" && !input.roundTrip) input.outbound else input.returnDistance))
                }
                if (input.roundTrip) {
                    NumericField(input.returnDistance, { update(input.copy(returnDistance = it)) }, "Return distance (km)")
                    Text("Return distance starts with the outbound distance and can be edited. Return tolls are entered separately.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item {
            LedgerCard {
                Text("Fuel settings", style = MaterialTheme.typography.titleMedium)
                NumericField(input.consumption, { update(input.copy(consumption = it)) }, "Consumption (L/100 km)")
                NumericField(input.price, { update(input.copy(price = it)) }, "Petrol price (RM/L)")
                Text("Starts with your saved defaults. Changes apply to this journey only.", style = MaterialTheme.typography.bodySmall)
            }
        }
        item { FeeSection("Outbound toll fees", input.outboundTolls, "Toll", { update(input.copy(outboundTolls = it)) }) }
        if (input.roundTrip) item { FeeSection("Return toll fees", input.returnTolls, "Toll", { update(input.copy(returnTolls = it)) }) }
        item {
            LedgerCard {
                Text("Parking", style = MaterialTheme.typography.titleMedium)
                NumericField(input.parking, { update(input.copy(parking = it)) }, "Total parking for the journey (RM)")
                Text("Fees are totals for the journey; they are never doubled automatically.", style = MaterialTheme.typography.bodySmall)
            }
        }
        item { FeeSection("Other journey fees", input.others, "Fee", { update(input.copy(others = it)) }) }
        item {
            LedgerCard {
                Text("Passenger cost sharing", style = MaterialTheme.typography.titleMedium)
                NumericField(input.people, { update(input.copy(people = it)) }, "People sharing (including you)")
                ShareToggle("Share fuel", input.shareFuel) { update(input.copy(shareFuel = it)) }
                ShareToggle("Share tolls", input.shareTolls) { update(input.copy(shareTolls = it)) }
                ShareToggle("Share parking", input.shareParking) { update(input.copy(shareParking = it)) }
                ShareToggle("Share other fees", input.shareOther) { update(input.copy(shareOther = it)) }
            }
        }
        item {
            LedgerCard(emphasized = true) {
                Text("Total estimated journey cost", style = MaterialTheme.typography.titleLarge)
                estimate.getOrNull()?.let { cost ->
                    JourneyCostSummary(cost)
                } ?: Text(estimate.exceptionOrNull()?.message ?: "Check the journey inputs.", color = MaterialTheme.colorScheme.error)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                notice?.let { Text(it) }
                Button({
                    runCatching { savedJourney(id, input.plan()) }.onSuccess {
                        vm.save(it) { error = null; notice = "Journey saved locally." }
                    }.onFailure { error = it.message }
                }, enabled = !busy && estimate.isSuccess, modifier = Modifier.fillMaxWidth()) { Text(if (saved != null) "Update saved journey" else "Save journey") }
                OutlinedButton({ id = UUID.randomUUID().toString(); update(JourneyInput(consumption = settings.consumption.toString(), price = settings.petrolPrice)); error = null; notice = null }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Start new journey") }
            }
        }
        item { SectionTitle("Saved journey history", "Tap Edit to load a route. Duplicate keeps the route and fees and uses your latest default petrol price.") }
        if (journeys.isEmpty()) item { EmptyState("No saved journeys", "Save a plan to reuse its locations, distance and fees offline.") }
        items(journeys, key = { it.id }) { journey ->
            val plan = journey.plan()
            LedgerCard {
                Text("${plan.origin} → ${plan.destination}", style = MaterialTheme.typography.titleMedium)
                Text("${dateTime(journey.timestamp)} · ${if (plan.roundTrip) "Round-trip" else "One-way"}", style = MaterialTheme.typography.bodySmall)
                Text(Money.format(journeyCost(plan).totalCents), style = MaterialTheme.typography.titleLarge)
                Button({ id = journey.id; update(journeyInput(plan)); notice = "Saved journey loaded."; error = null; scope.launch { listState.scrollToItem(0) } }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Edit / load route") }
                OutlinedButton({ id = UUID.randomUUID().toString(); update(journeyInput(plan.copy(petrolPrice = settings.petrolPrice))); notice = "Duplicate loaded. Review fees and save as a new journey."; error = null; scope.launch { listState.scrollToItem(0) } }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Duplicate with current petrol price") }
                TextButton({ deleting = journey }, enabled = !busy) { Text("Delete journey") }
            }
        }
    }
    deleting?.let { journey -> DeleteConfirmation("Delete saved journey?", "Only this plan is removed. Your actual expenses and refuelling records stay available.", busy, { deleting = null }) {
        vm.delete(journey.id) { deleting = null; if (id == journey.id) id = UUID.randomUUID().toString() }
    } }
    operationError?.let { message -> AlertDialog(onDismissRequest = vm::dismissError, title = { Text("Unable to update journey") }, text = { PrivacyDialogWindow(); Text(message) }, confirmButton = { TextButton(vm::dismissError) { Text("OK") } }) }
}

@Composable
private fun LocationSuggestions(value: String, locations: List<String>, choose: (String) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        locations.filter { it.startsWith(value, ignoreCase = true) && it != value }.take(6).forEach {
            SuggestionChip(onClick = { choose(it) }, label = { Text(it) }, modifier = Modifier.heightIn(min = 48.dp))
        }
    }
}
@Composable
private fun ShareToggle(label: String, checked: Boolean, change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Checkbox(checked, change); Text(label, Modifier.weight(1f)) }
}
@Composable
private fun FeeSection(title: String, entries: List<FeeInput>, defaultName: String, update: (List<FeeInput>) -> Unit) {
    LedgerCard {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text("Enter a total as one entry, or add named segments. No online prices are fetched.", style = MaterialTheme.typography.bodySmall)
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
                    Text("Tolls ${Money.format(cost.tollCents)} · Parking ${Money.format(cost.parkingCents)} · Other ${Money.format(cost.otherCents)}")
                    Text("Shared ${Money.format(cost.sharedCents)} ÷ ${cost.people} = ${Money.format(cost.perPersonCents)} per person", style = MaterialTheme.typography.titleMedium)
                    Text("Per-person amounts are rounded to the nearest sen. These estimates do not record expenses or transfers.", style = MaterialTheme.typography.bodySmall)
    }
}
