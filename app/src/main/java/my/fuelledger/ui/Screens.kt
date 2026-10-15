package my.fuelledger.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import my.fuelledger.LedgerState
import my.fuelledger.data.*
import my.fuelledger.domain.*
import java.time.*
import java.time.format.DateTimeFormatter

private val screenPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 24.dp)
private fun spending(state: LedgerState) = Spending(state.expenses.map { it.point() })

@Composable
fun HomeScreen(state: LedgerState, addExpense: () -> Unit, addRefuel: () -> Unit, edit: (Expense) -> Unit) {
    val calendar = currentCalendar()
    val today = calendar.today
    val month = YearMonth.from(today)
    val totals = remember(state.expenses, calendar.zone) { spending(state) }
    val fuel = state.refuels.filter { YearMonth.from(localDate(it.timestamp)) == month }.sumOf { it.cents }
    LazyColumn(contentPadding = screenPadding, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { SectionTitle("Your money, at a glance", today.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Malaysia))) }
        item {
            Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(28.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("SPENDING THIS MONTH", style = MaterialTheme.typography.labelLarge)
                    Text(Money.format(totals.month(month)), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                    Text("Includes your refuelling payments", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Stat("Today", Money.format(totals.today(today)), Modifier.weight(1f))
                Stat("Monthly fuel", Money.format(fuel), Modifier.weight(1f))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = addExpense, modifier = Modifier.weight(1f)) { Icon(Icons.Outlined.Add, null); Spacer(Modifier.width(6.dp)); Text("Expense") }
                FilledTonalButton(onClick = addRefuel, modifier = Modifier.weight(1f)) { Icon(Icons.Outlined.LocalGasStation, null); Spacer(Modifier.width(6.dp)); Text("Refuel") }
            }
        }
        item { SectionTitle("Recent transactions", "Tap a transaction to view or edit") }
        if (state.expenses.isEmpty()) item { EmptyState("Start your local ledger", "Add an expense or a refuel. Everything stays on your phone.") }
        items(state.expenses.take(10), key = { it.id }) { expense -> ExpenseRow(expense, state.categories.find { it.id == expense.categoryId }) { edit(expense) } }
    }
}

@Composable
fun TripEstimator(settings: AppSettings) {
    var distance by rememberSaveable { mutableStateOf("150") }
    var consumption by rememberSaveable(settings.consumption) { mutableStateOf(settings.consumption.toString()) }
    var price by rememberSaveable(settings.petrolPrice) { mutableStateOf(settings.petrolPrice) }
    val calculation = remember(distance, consumption, price) {
        runCatching { estimateTrip(measurement(distance, "Distance"), measurement(consumption, "Consumption"), price.toBigDecimal()) }
    }
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Trip cost estimator", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("Estimated · based on the inputs below", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
            NumericField(distance, { distance = it }, "Trip distance (km)")
            NumericField(consumption, { consumption = it }, "Consumption (L/100 km)")
            NumericField(price, { price = it }, "Petrol price (RM/L)")
            calculation.getOrNull()?.let { estimate ->
                HorizontalDivider()
                Text(Money.format(estimate.costCents), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                Text("${number(estimate.litres)} L of fuel · ${number(estimate.kmPerLitre)} km/L")
                Text("RM ${number(estimate.costPerKm, 4)} per km", style = MaterialTheme.typography.bodyMedium)
            } ?: Text("Enter positive distance, consumption and price to calculate.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
    }
}
@Composable
fun FuelScreen(state: LedgerState, add: () -> Unit, edit: (Refuel) -> Unit) {
    val month = YearMonth.from(currentCalendar().today)
    val current = state.refuels.filter { YearMonth.from(localDate(it.timestamp)) == month }
    val measured = remember(state.refuels) { efficiencies(state.refuels.map { it.point() }) }
    val last = measured.lastOrNull()
    val average = if (measured.isEmpty()) null else measured.sumOf { it.distance } / measured.sumOf { it.litres }
    LazyColumn(contentPadding = screenPadding, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { SectionTitle("Fuel, with a clearer picture", "Estimates for planning. Full tanks for measurement.") }
        item { TripEstimator(state.settings) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Stat("Fuel this month", Money.format(current.sumOf { it.cents }), Modifier.weight(1f), "${current.size} refuelling sessions")
                Stat("Latest price", state.refuels.firstOrNull()?.let { "RM ${number(it.pricePerLitre, 3)}" } ?: "—", Modifier.weight(1f), "Per litre")
            }
        }
        item {
            if (last == null) EmptyState("Insufficient refuelling data", "Record two full tanks with increasing odometer readings. Partial fills between them are included.")
            else OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("MEASURED · FULL TANK TO FULL TANK", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                    Text("${number(last.kmPerLitre)} km/L", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text("Latest: ${number(last.litresPer100Km)} L/100 km · RM ${number(last.costPerKm, 4)}/km")
                    Text("Average: ${number(average!!)} km/L across ${measured.size} valid cycles", style = MaterialTheme.typography.bodyMedium)
                    Text("Average = total measured distance ÷ total measured litres. The baseline tank's fuel is excluded.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item { SpendingBars("Fuel spending by month", (5L downTo 0L).map { offset ->
            val selected = month.minusMonths(offset)
            selected.format(DateTimeFormatter.ofPattern("MMM yy", Malaysia)) to state.refuels.filter { YearMonth.from(localDate(it.timestamp)) == selected }.sumOf { it.cents }
        }, MaterialTheme.colorScheme.secondary) }
        item { Button(onClick = add, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.Add, null); Text(" Add refuel") } }
        item { SectionTitle("Refuelling history", "Newest first · tap to view or edit") }
        if (state.refuels.isEmpty()) item { EmptyState("No refuels yet", "Your first full tank establishes the odometer baseline.") }
        items(state.refuels, key = { it.id }) { refuel ->
            val cycle = measured.find { it.endId == refuel.id }
            Card(onClick = { edit(refuel) }, modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(refuel.station.ifBlank { "Refuelling" }, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Text(Money.format(refuel.cents), fontWeight = FontWeight.Bold)
                    }
                    Text(dateTime(refuel.timestamp), style = MaterialTheme.typography.bodySmall)
                    Text("${number(refuel.odometer, 1)} km · ${number(refuel.litres)} L · RM ${number(refuel.pricePerLitre, 3)}/L")
                    Text(if (refuel.fullTank) "Full tank" else "Partial fill", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                    if (cycle != null) Text("Measured: ${number(cycle.kmPerLitre)} km/L · ${number(cycle.litresPer100Km)} L/100 km\n${number(cycle.distance, 1)} km using ${number(cycle.litres)} L · RM ${number(cycle.costPerKm, 4)}/km", style = MaterialTheme.typography.bodySmall)
                    if (refuel.notes.isNotBlank()) Text(refuel.notes, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
fun ExpensesScreen(state: LedgerState, add: () -> Unit, edit: (Expense) -> Unit) {
    val calendar = currentCalendar()
    val today = calendar.today
    val currentMonth = YearMonth.from(today)
    var startText by rememberSaveable { mutableStateOf(currentMonth.atDay(1).toString()) }
    var endText by rememberSaveable { mutableStateOf(currentMonth.atEndOfMonth().toString()) }
    var category by rememberSaveable { mutableStateOf("") }
    var query by rememberSaveable { mutableStateOf("") }
    var sort by rememberSaveable { mutableStateOf("Newest") }
    val start = LocalDate.parse(startText)
    val end = LocalDate.parse(endText)
    val rangeValid = !end.isBefore(start)
    val totals = remember(state.expenses, calendar.zone) { spending(state) }
    val filtered = remember(state.expenses, start, end, category, query, sort) {
        val records = state.expenses.filter { rangeValid && localDate(it.timestamp) in start..end &&
            (category.isEmpty() || it.categoryId == category) && it.description.contains(query, ignoreCase = true) }
        when (sort) { "Oldest" -> records.sortedBy { it.timestamp }; "Highest" -> records.sortedByDescending { it.cents }; else -> records.sortedByDescending { it.timestamp } }
    }
    val groups = filtered.groupBy { localDate(it.timestamp) }.entries.toList().let { entries ->
        when (sort) { "Oldest" -> entries.sortedBy { it.key }; "Highest" -> entries.sortedByDescending { it.value.maxOf { e -> e.cents } }; else -> entries.sortedByDescending { it.key } }
    }
    LazyColumn(contentPadding = screenPadding, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { SectionTitle("Every expense, accounted for", "Linked refuels are included once") }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Stat("This week", Money.format(totals.week(today)), Modifier.weight(1f), "Monday–Sunday")
                Stat("This year", Money.format(totals.year(today)), Modifier.weight(1f))
            }
        }
        item { Stat("This month", Money.format(totals.month(currentMonth)), Modifier.fillMaxWidth()) }
        item { Button(onClick = add, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.Add, null); Text(" Add expense") } }
        item { SectionTitle("Transaction history") }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DateButton(start, "From", { startText = it.toString() }, Modifier.fillMaxWidth())
                DateButton(end, "To", { endText = it.toString() }, Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { startText = currentMonth.atDay(1).toString(); endText = currentMonth.atEndOfMonth().toString() }) { Text("This month") }
                    TextButton(onClick = {
                        startText = state.expenses.minOfOrNull { localDate(it.timestamp) }?.toString() ?: today.toString()
                        endText = maxOf(today, state.expenses.maxOfOrNull { localDate(it.timestamp) } ?: today).toString()
                    }) { Text("All history") }
                }
                if (!rangeValid) Text("End date must be on or after start date.", color = MaterialTheme.colorScheme.error)
                TextField(query, { query = it }, "Search descriptions", 200)
                Choice("Sort", sort, listOf("Newest", "Oldest", "Highest").map { it to it }) { sort = it }
            }
        }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = category.isEmpty(), onClick = { category = "" }, label = { Text("All categories") })
                state.categories.forEach { item -> FilterChip(selected = category == item.id, onClick = { category = item.id }, label = { Text(item.name) }) }
            }
        }
        item {
            Stat("Selected transactions · ${filtered.size}", Money.format(filtered.sumOf { it.cents }), Modifier.fillMaxWidth(),
                if (rangeValid) "Average per day: RM ${number(filtered.sumOf { it.cents } / 100.0 / (java.time.temporal.ChronoUnit.DAYS.between(start, end) + 1))}" else null)
        }
        if (filtered.isEmpty()) item { EmptyState("No matching transactions", "Try another date range, category or search, or add your first expense.") }
        if (sort == "Highest") {
            filtered.forEachIndexed { index, expense ->
                val date = localDate(expense.timestamp)
                if (index == 0 || localDate(filtered[index - 1].timestamp) != date) item(key = "highest-day:${expense.id}") {
                    Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(dateLabel(date), fontWeight = FontWeight.SemiBold)
                        Text("Day: ${Money.format(filtered.filter { localDate(it.timestamp) == date }.sumOf { it.cents })}", style = MaterialTheme.typography.labelLarge)
                    }
                }
                item(key = expense.id) { ExpenseRow(expense, state.categories.find { it.id == expense.categoryId }) { edit(expense) } }
            }
        } else groups.forEach { (date, entries) ->
            item(key = "day:$date") {
                Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(dateLabel(date), fontWeight = FontWeight.SemiBold)
                    Text(Money.format(entries.sumOf { it.cents }), style = MaterialTheme.typography.labelLarge)
                }
            }
            items(entries, key = { it.id }) { expense -> ExpenseRow(expense, state.categories.find { it.id == expense.categoryId }) { edit(expense) } }
        }
    }
}

@Composable
fun AnalyticsScreen(state: LedgerState) {
    val calendar = currentCalendar()
    val today = calendar.today
    var monthString by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    val month = YearMonth.parse(monthString)
    val totals = remember(state.expenses, calendar.zone) { spending(state) }
    val current = totals.month(month)
    val previous = totals.month(month.minusMonths(1))
    val distribution = totals.categories(month.atDay(1), month.atEndOfMonth()).entries.sortedByDescending { it.value }
    LazyColumn(contentPadding = screenPadding, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { SectionTitle("Patterns worth seeing", "Explore spending without leaving your phone") }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { monthString = month.minusMonths(1).toString() }) { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, "Previous month") }
                Text(month.format(DateTimeFormatter.ofPattern("MMMM yyyy", Malaysia)), style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = { monthString = month.plusMonths(1).toString() }) { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, "Next month") }
            }
        }
        item {
            Stat("Selected month", Money.format(current), Modifier.fillMaxWidth(),
                if (previous == 0L) "Previous month: ${Money.format(previous)} · percentage unavailable"
                else "${if (current >= previous) "+" else ""}${number((current - previous) * 100.0 / previous, 1)}% vs previous month (${Money.format(previous)})")
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Stat("Today", Money.format(totals.today(today)), Modifier.weight(1f))
                Stat("This week", Money.format(totals.week(today)), Modifier.weight(1f))
            }
        }
        item { Stat("Average daily spending", "RM ${number(totals.average(month.atDay(1), month.atEndOfMonth()))}", Modifier.fillMaxWidth(), "All ${month.lengthOfMonth()} calendar days, including days with no spending") }
        item { SectionTitle("Category breakdown", distribution.firstOrNull()?.let { "Highest: ${state.categories.find { c -> c.id == it.key }?.name}" } ?: "No spending this month") }
        if (distribution.isEmpty()) item { EmptyState("A fresh month", "Charts will fill in when you add transactions for this month.") }
        items(distribution, key = { it.key }) { (id, cents) ->
            val category = state.categories.find { it.id == id }
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        CategoryBadge(category)
                        Column(Modifier.weight(1f)) { Text(category?.name.orEmpty(), fontWeight = FontWeight.SemiBold); Text("${number(cents * 100.0 / current, 1)}%", style = MaterialTheme.typography.bodySmall) }
                        Text(Money.format(cents), fontWeight = FontWeight.SemiBold)
                    }
                    LinearProgressIndicator(progress = { (cents.toDouble() / current).toFloat() }, modifier = Modifier.fillMaxWidth(), color = category?.let { Color(it.color.toULong()) } ?: MaterialTheme.colorScheme.primary)
                }
            }
        }
        item {
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Daily expenditure", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Scroll across the month · day / MYR", style = MaterialTheme.typography.bodySmall)
                    val data = (1..month.lengthOfMonth()).map { totals.today(month.atDay(it)) }
                    val max = data.maxOrNull()?.coerceAtLeast(1) ?: 1L
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Bottom) {
                        data.forEachIndexed { index, cents ->
                            Column(Modifier.width(52.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(Money.decimal(cents), style = MaterialTheme.typography.labelSmall)
                                Box(Modifier.height(120.dp).width(24.dp), contentAlignment = Alignment.BottomCenter) {
                                    Box(Modifier.fillMaxWidth().height(if (cents > 0) (120 * cents.toDouble() / max).dp else 2.dp)
                                        .background(if (cents > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(4.dp)))
                                }
                                Text((index + 1).toString(), style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }
        }
        item { SpendingBars("Monthly expenditure trend", (5L downTo 0L).map { offset -> val m = month.minusMonths(offset); m.format(DateTimeFormatter.ofPattern("MMM yy", Malaysia)) to totals.month(m) }) }
        item { SpendingBars("Monthly fuel trend", (5L downTo 0L).map { offset -> val m = month.minusMonths(offset); m.format(DateTimeFormatter.ofPattern("MMM yy", Malaysia)) to state.refuels.filter { YearMonth.from(localDate(it.timestamp)) == m }.sumOf { it.cents } }, MaterialTheme.colorScheme.secondary) }
    }
}
