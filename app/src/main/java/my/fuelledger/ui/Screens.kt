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
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import my.fuelledger.LedgerState
import my.fuelledger.data.*
import my.fuelledger.domain.*
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

private fun spending(state: LedgerState, zone: ZoneId) = Spending(state.expenses.map { it.point() }, zone)
private val monthFormat = DateTimeFormatter.ofPattern("MMMM yyyy", Malaysia)
private val shortMonthFormat = DateTimeFormatter.ofPattern("MMM yy", Malaysia)

@Composable
fun HomeScreen(state: LedgerState, addExpense: () -> Unit, addRefuel: () -> Unit, edit: (Expense) -> Unit,
    planner: (@Composable () -> Unit)? = null, scanReceipt: (() -> Unit)? = null, openSettings: (() -> Unit)? = null,
    finance: my.fuelledger.FinanceState = my.fuelledger.FinanceState(), addIncome: () -> Unit = {},
    openCalendar: () -> Unit = {}, openPlanner: () -> Unit = {}, upcoming: () -> Unit = openPlanner) {
    OverviewDashboard(state, finance, addExpense, addIncome, scanReceipt ?: {}, openCalendar, openSettings, openPlanner, upcoming)
}

@Composable
fun FuelScreen(state: LedgerState, add: () -> Unit, edit: (Refuel) -> Unit, openSettings: (() -> Unit)? = null) {
    val calendar = currentCalendar()
    val month = YearMonth.from(calendar.today)
    var chartMonths by rememberSaveable { mutableStateOf(3) }
    val current = state.refuels.filter { YearMonth.from(localDate(it.timestamp)) == month }
    val estimates = remember(state.refuels) { fillEstimates(state.refuels.map { it.point() }) }
    val latest = estimates.lastOrNull()
    val measured = remember(state.refuels) { efficiencies(state.refuels.map { it.point() }) }
    val measuredLast = measured.lastOrNull()
    val measuredAverage = if (measured.isEmpty()) null else measured.sumOf { it.distance } / measured.sumOf { it.litres }
    val newest = state.refuels.maxByOrNull { it.timestamp }
    val records = state.refuels.sortedByDescending { it.timestamp }
    LazyColumn(contentPadding = ledgerScreenPadding, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { ScreenHeading("Fuel tracker", "Refuelling records and statistics", ledgerSymbol("fuel"), openSettings) }
        item {
            LedgerCard(emphasized = true) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Average fuel efficiency", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Icon(ledgerSymbol("speed"), null, tint = MaterialTheme.colorScheme.primary)
                }
                Text(measuredAverage?.let { "${number(it)} km/L" } ?: "— km/L", style = MaterialTheme.typography.headlineLarge)
                Text(historicalConsumption(state.refuels.map { it.point() })?.let { "${number(it)} L/100 km" } ?: "Complete two full-tank readings to begin.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                HorizontalDivider(color = MaterialTheme.colorScheme.primary.copy(alpha = .18f))
                Text("WEIGHTED · FULL-TANK INTERVALS", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                ExpandableHelp("Per-fill estimates") { Text("Distance since the previous fill ÷ litres purchased. Tank levels vary; journey planning uses complete full-tank cycles instead.", style = MaterialTheme.typography.bodySmall) }
            }
        }
        item { ResponsivePair(
            first = { Stat("Monthly fuel cost", Money.format(current.sumOf { it.cents }), it, "${current.size} fills · ${month.format(DateTimeFormatter.ofPattern("MMMM", Malaysia))}") },
            second = { Stat("Latest price / litre", newest?.let { "RM ${number(it.pricePerLitre, 3)}" } ?: "—", it, newest?.let { dateLabel(localDate(it.timestamp)) } ?: "No refuels yet") }
        ) }
        item { LedgerAction("Add Refuel", ledgerSymbol("add"), Modifier.fillMaxWidth(), onClick = add) }
        item { FuelTrend(measured.map { cycle ->
            val record = state.refuels.find { it.id == cycle.endId }
            (record?.let { dateLabel(localDate(it.timestamp)) } ?: "Cycle") to cycle.kmPerLitre
        }, "Valid full-tank intervals · km/L") }
        item { VehicleAnalysisCard(state) }
        item { FuelPriceHistory(state.refuels) }
        item {
            LedgerCard {
                Text("Historical consumption", style = MaterialTheme.typography.titleMedium)
                Text(historicalConsumption(state.refuels.map { it.point() })?.let { "${number(it)} L/100 km" } ?: "Not enough fuel data", style = MaterialTheme.typography.headlineSmall)
                if (measuredLast != null) {
                    Text("${number(measuredLast.kmPerLitre)} km/L", style = MaterialTheme.typography.headlineSmall)
                    Text("Latest measured cycle · ${number(measuredLast.litresPer100Km)} L/100 km · RM ${number(measuredLast.costPerKm, 4)}/km", style = MaterialTheme.typography.bodySmall)
                    Text("Average ${number(measuredAverage!!)} km/L across ${measured.size} valid cycles", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                }
                ExpandableHelp("Full-tank calculation") { Text("Two full-tank records close a cycle. Partial fills between them are included; the first tank's purchase is excluded. The historical average weights cycles by distance.", style = MaterialTheme.typography.bodySmall) }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(3, 6, 12).forEach { count -> FilterChip(chartMonths == count, { chartMonths = count },
                    label = { Text("$count months") }, modifier = Modifier.heightIn(min = 48.dp)) }
            }
        }
        item { SpendingBars("Fuel spending by month", fuelSpendingMonths(state.refuels, calendar.today, chartMonths, calendar.zone), MaterialTheme.colorScheme.secondary) }
        item { SectionTitle("Refuelling history", "Newest first · tap a fill to edit or view its receipt") }
        if (records.isEmpty()) item { EmptyState("Ready for your first fill", "Record the odometer, litres and cost. Your next fill unlocks the first consumption estimate.") }
        items(records, key = { it.id }) { refuel -> RefuelHistoryCard(refuel, estimates.find { it.endId == refuel.id }, measured.find { it.endId == refuel.id }) { edit(refuel) } }
    }
}

@Composable
private fun RefuelHistoryCard(refuel: Refuel, estimate: FillEstimate?, measured: Efficiency?, edit: () -> Unit) {
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val stacked = maxWidth < 330.dp || fontScale >= 1.3f
        Card(onClick = edit, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                    Box(Modifier.size(42.dp).background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(13.dp)), contentAlignment = Alignment.Center) {
                        Icon(ledgerSymbol("fuel"), null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(22.dp))
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(refuel.station.ifBlank { "Refuelling" }, style = MaterialTheme.typography.titleMedium)
                        Text(dateTime(refuel.timestamp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (stacked) Text(Money.format(refuel.cents), style = MaterialTheme.typography.titleMedium)
                    }
                    if (!stacked) Text(Money.format(refuel.cents), style = MaterialTheme.typography.titleMedium, modifier = Modifier.widthIn(max = 145.dp))
                }
                Text("${number(refuel.odometer, 1)} km ODO · ${number(refuel.litres)} L · RM ${number(refuel.pricePerLitre, 3)}/L", style = MaterialTheme.typography.bodySmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(8.dp)) {
                        Text(if (refuel.fullTank) "Full tank" else "Partial fill", Modifier.padding(horizontal = 9.dp, vertical = 5.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                    if (refuel.fuelType.isNotBlank()) Surface(color = MaterialTheme.colorScheme.surfaceContainerHighest, shape = RoundedCornerShape(8.dp)) {
                        Text(refuel.fuelType, Modifier.padding(horizontal = 9.dp, vertical = 5.dp), style = MaterialTheme.typography.labelSmall)
                    }
                    if (refuel.receiptFile != null) Row(Modifier.padding(vertical = 5.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(ledgerSymbol("receipt"), null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                        Text("Receipt attached", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
                if (refuel.stationLocation.isNotBlank()) Text(refuel.stationLocation, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (estimate != null) Text("Estimate ${number(estimate.kmPerLitre)} km/L · ${number(estimate.litresPer100Km)} L/100 km\n${number(estimate.distance, 1)} km since previous fill · RM ${number(estimate.costPerKm, 4)}/km", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                if (measured != null) Text("Full-tank cycle ${number(measured.kmPerLitre)} km/L · ${number(measured.distance, 1)} km using ${number(measured.litres)} L", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                if (refuel.notes.isNotBlank()) Text(refuel.notes, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun ExpensesScreen(state: LedgerState, add: () -> Unit, edit: (Expense) -> Unit, planner: (@Composable () -> Unit)? = null, scanReceipt: (() -> Unit)? = null, openSettings: (() -> Unit)? = null,
    vm: my.fuelledger.LedgerViewModel? = null, openCalendar: () -> Unit = {}, duplicate: (Expense) -> Unit = {}, refund: (Expense) -> Unit = {}) {
    val calendar = currentCalendar()
    val today = calendar.today
    val currentMonth = YearMonth.from(today)
    var startText by rememberSaveable { mutableStateOf(currentMonth.atDay(1).toString()) }
    var endText by rememberSaveable { mutableStateOf(currentMonth.atEndOfMonth().toString()) }
    var category by rememberSaveable { mutableStateOf("") }
    var query by rememberSaveable { mutableStateOf("") }
    var sort by rememberSaveable { mutableStateOf("Newest") }
    var filtersOpen by rememberSaveable { mutableStateOf(false) }
    val start = LocalDate.parse(startText)
    val end = LocalDate.parse(endText)
    val rangeValid = !end.isBefore(start)
    val totals = remember(state.expenses, calendar.zone) { spending(state, calendar.zone) }
    val filtered = remember(state.expenses, start, end, category, query, sort) {
        val records = state.expenses.filter { rangeValid && localDate(it.timestamp) in start..end &&
            (category.isEmpty() || it.categoryId == category) && matchesExpense(it, query, state.categories, calendar.zone) }
        when (sort) { "Oldest" -> records.sortedBy { it.timestamp }; "Highest" -> records.sortedByDescending { it.cents }; else -> records.sortedByDescending { it.timestamp } }
    }
    val groups = filtered.groupBy { localDate(it.timestamp) }.entries.toList().let { entries ->
        if (sort == "Oldest") entries.sortedBy { it.key } else entries.sortedByDescending { it.key }
    }
    LazyColumn(contentPadding = ledgerScreenPadding, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { ScreenHeading("Expenses", "Recorded transactions", ledgerSymbol("expenses"), openSettings) }
        item { HeroAmount("This month's expenditure", Money.format(totals.month(currentMonth)), "${currentMonth.format(monthFormat)} · fuel counted once") }
        item { ResponsivePair(
            first = { Stat("Today", Money.format(totals.today(today)), it) },
            second = { Stat("This week", Money.format(totals.week(today)), it, "Monday–Sunday") }
        ) }
        item { LedgerAction("Add Expense", ledgerSymbol("add"), Modifier.fillMaxWidth(), onClick = add) }
        item { TextButton(openCalendar) { Text("Spending Calendar") } }
        if (vm != null) item { ExpenseFavourites(vm, state, add, duplicate) }
        if (vm != null) item { TransactionUndoBar(vm) }
        item { SectionTitle("Transaction history", "${dateLabel(start)} – ${dateLabel(end)}") }
        item {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = category.isEmpty(), onClick = { category = "" }, modifier = Modifier.heightIn(min = 48.dp), label = { Text("All") })
                displayCategories(state.categories).forEach { item ->
                    val label = when (item.id) { "food" -> "Food"; FUEL_CATEGORY -> "Fuel"; else -> item.name }
                    FilterChip(selected = category == item.id, onClick = { category = item.id }, modifier = Modifier.heightIn(min = 48.dp),
                        leadingIcon = { Icon(categoryIcon(item.icon), null, Modifier.size(17.dp)) }, label = { Text(label) })
                }
            }
        }
        item {
            LedgerCard {
                TextField(query, { query = it }, "Search descriptions", 200)
                OutlinedButton(onClick = { filtersOpen = !filtersOpen }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = RoundedCornerShape(12.dp)) {
                    Icon(ledgerSymbol("tune"), null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                    Text(if (filtersOpen) "Hide date and sort filters" else "Date range & sorting")
                }
                if (filtersOpen) {
                    DateButton(start, "From", { startText = it.toString() }, Modifier.fillMaxWidth())
                    DateButton(end, "To", { endText = it.toString() }, Modifier.fillMaxWidth())
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { startText = currentMonth.atDay(1).toString(); endText = currentMonth.atEndOfMonth().toString() }) { Text("This month") }
                        TextButton(onClick = {
                            startText = state.expenses.minOfOrNull { localDate(it.timestamp) }?.toString() ?: today.toString()
                            endText = maxOf(today, state.expenses.maxOfOrNull { localDate(it.timestamp) } ?: today).toString()
                        }) { Text("All history") }
                    }
                    Choice("Sort", sort, listOf("Newest", "Oldest", "Highest").map { it to it }) { sort = it }
                }
                if (!rangeValid) Text("End date must be on or after start date.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                Text("${filtered.size} transactions · ${Money.format(filtered.sumOf { it.cents })}", style = MaterialTheme.typography.titleSmall)
                if (rangeValid) Text("Daily average: RM ${number(filtered.sumOf { it.cents } / 100.0 / (ChronoUnit.DAYS.between(start, end) + 1))}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (filtered.isEmpty()) item { EmptyState("No matching transactions", "Try another date range, category or search, or add your first expense.") }
        if (sort == "Highest") {
            filtered.forEachIndexed { index, expense ->
                val date = localDate(expense.timestamp)
                if (index == 0 || localDate(filtered[index - 1].timestamp) != date) item(key = "highest-day:${expense.id}") {
                    DayHeading(date, filtered.filter { localDate(it.timestamp) == date }.sumOf { it.cents })
                }
                item(key = expense.id) { TransactionActions(expense, state, edit, duplicate, refund, vm) }
            }
        } else groups.forEach { (date, entries) ->
            item(key = "day:$date") { DayHeading(date, entries.sumOf { it.cents }) }
            items(entries, key = { it.id }) { expense -> TransactionActions(expense, state, edit, duplicate, refund, vm) }
        }
        item { Stat("This year", Money.format(totals.year(today)), Modifier.fillMaxWidth(), "All recorded expenses in ${today.year}") }
    }
}

@Composable
private fun DayHeading(date: LocalDate, cents: Long) {
    FlowRow(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(dateLabel(date), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        Text(Money.format(cents), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun AnalyticsScreen(state: LedgerState, finance: my.fuelledger.FinanceState? = null, openSettings: (() -> Unit)? = null) {
    val calendar = currentCalendar()
    val today = calendar.today
    var monthString by rememberSaveable { mutableStateOf(YearMonth.from(today).toString()) }
    val month = YearMonth.parse(monthString)
    var customRange by rememberSaveable { mutableStateOf(false) }
    var startText by rememberSaveable { mutableStateOf(month.atDay(1).toString()) }
    var endText by rememberSaveable { mutableStateOf(month.atEndOfMonth().toString()) }
    val start = if (customRange) LocalDate.parse(startText) else month.atDay(1)
    val end = if (customRange) LocalDate.parse(endText) else month.atEndOfMonth()
    val valid = !end.isBefore(start)
    val totals = remember(state.expenses, calendar.zone) { spending(state, calendar.zone) }
    val current = if (valid) totals.total(start, end) else 0L
    val previous = totals.month(month.minusMonths(1))
    val selectedMonthTotal = totals.month(month)
    val distribution = if (valid) totals.categories(start, end).entries.sortedByDescending { it.value } else emptyList()
    // Keep arbitrary ranges responsive while saying exactly which window each chart shows.
    val trendEndMonth = if (customRange) YearMonth.from(end) else month
    val firstTrendMonth = if (customRange && valid) maxOf(YearMonth.from(start), trendEndMonth.minusMonths(11)) else month.minusMonths(5)
    val trendMonths = if (valid) generateSequence(firstTrendMonth) { it.plusMonths(1) }.takeWhile { it <= trendEndMonth }.toList() else emptyList()
    fun trendBounds(m: YearMonth): Pair<LocalDate, LocalDate> =
        if (customRange) maxOf(m.atDay(1), start) to minOf(m.atEndOfMonth(), end) else m.atDay(1) to m.atEndOfMonth()
    LazyColumn(contentPadding = ledgerScreenPadding, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { ScreenHeading("Analytics", "Understand your spending, at your own pace.", ledgerSymbol("analytics"), openSettings) }
        if (finance != null && !customRange) item {
            val plan = finance.months.find { it.month == month.toString() }?.occurrences() ?: plannedOccurrences(finance.recurring, month)
            val money = monthlyMoney(month, plan, finance.income, state.expenses, calendar.zone)
            LedgerCard {
                Text("Monthly plan vs actual · $month", style = MaterialTheme.typography.titleMedium)
                Text("Expected income ${Money.format(money.expected)} · fixed commitments ${Money.format(money.fixed)}")
                Text("Planned available ${Money.format(money.plannedAvailable)}")
                Text("Actual net cash flow ${Money.format(money.actualNet)} · remaining allowance ${Money.format(money.remainingAllowance)}")
                Text("Charts below show actual recorded expenses, including paid fixed commitments.", style = MaterialTheme.typography.bodySmall)
            }
        }
        item {
            LedgerCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { monthString = month.minusMonths(1).toString(); customRange = false }) { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, "Previous month") }
                    Text(month.format(monthFormat), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    IconButton(onClick = { monthString = month.plusMonths(1).toString(); customRange = false }) { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, "Next month") }
                }
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(!customRange, { customRange = false }, label = { Text("Selected month") }, modifier = Modifier.heightIn(min = 48.dp))
                    FilterChip(customRange, { if (!customRange) { startText = month.atDay(1).toString(); endText = month.atEndOfMonth().toString() }; customRange = true }, label = { Text("Custom range") }, modifier = Modifier.heightIn(min = 48.dp))
                }
                if (customRange) {
                    DateButton(start, "From", { startText = it.toString() }, Modifier.fillMaxWidth())
                    DateButton(end, "To", { endText = it.toString() }, Modifier.fillMaxWidth())
                }
                if (!valid) Text("End date must be on or after start date.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
        item { HeroAmount(if (customRange) "Selected period spending" else "Selected month spending", Money.format(current),
            if (customRange) "${dateLabel(start)} – ${dateLabel(end)}" else if (previous == 0L) "Previous month ${Money.format(previous)} · no percentage comparison"
            else "${if (selectedMonthTotal >= previous) "+" else ""}${number((selectedMonthTotal - previous) * 100.0 / previous, 1)}% vs previous month (${Money.format(previous)})", ledgerSymbol("analytics")) }
        item { ResponsivePair(
            first = { Stat("Daily average", if (valid) "RM ${number(totals.average(start, end))}" else "—", it, if (valid) "Across ${ChronoUnit.DAYS.between(start, end) + 1} calendar days" else "Choose a valid range") },
            second = { Stat("Fuel in this period", Money.format(if (valid) state.refuels.filter { localDate(it.timestamp) in start..end }.sumOf { it.cents } else 0), it, "Refuelling payments") }
        ) }
        item { CategoryDonut(distribution.map { (id, cents) -> state.categories.find { it.id == id } to cents }, current) }
        item { SectionTitle("Category breakdown", distribution.firstOrNull()?.let { "Highest spending: ${state.categories.find { c -> c.id == it.key }?.name ?: "Other"}" } ?: "No spending in this period") }
        items(distribution, key = { it.key }) { (id, cents) ->
            val category = state.categories.find { it.id == id }
            LedgerCard {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CategoryBadge(category)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(category?.name ?: "Other", style = MaterialTheme.typography.titleSmall)
                        Text(Money.format(cents), style = MaterialTheme.typography.titleMedium)
                        Text("${number(if (current > 0) cents * 100.0 / current else 0.0, 1)}% of this period", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                LinearProgressIndicator(progress = { if (current > 0) (cents.toDouble() / current).toFloat() else 0f }, modifier = Modifier.fillMaxWidth(), color = categoryTint(category), trackColor = MaterialTheme.colorScheme.surfaceContainerHighest)
            }
        }
        if (distribution.isEmpty()) item { EmptyState("Your next insight starts here", "Add expenses in this date range to see category totals and spending patterns.") }
        if (valid) {
            item { DailySpending(totals, start, end) }
            if (customRange && YearMonth.from(start) < firstTrendMonth) item {
                Text("Monthly charts show the latest 12 months of your selected period.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item { SpendingBars("Monthly expenditure trend", trendMonths.map { m ->
                val (from, to) = trendBounds(m)
                m.format(shortMonthFormat) to totals.total(from, to)
            }) }
            item { SpendingBars("Fuel expenditure history", trendMonths.map { m ->
                val (from, to) = trendBounds(m)
                m.format(shortMonthFormat) to state.refuels.filter { localDate(it.timestamp) in from..to }.sumOf { it.cents }
            }, MaterialTheme.colorScheme.secondary) }
        }
        item { ResponsivePair(
            first = { Stat("Today", Money.format(totals.today(today)), it) },
            second = { Stat("This week", Money.format(totals.week(today)), it, "Monday–Sunday") }
        ) }
    }
}

@Composable
private fun DailySpending(totals: Spending, start: LocalDate, end: LocalDate) {
    val chartStart = maxOf(start, end.minusDays(30))
    val days = generateSequence(chartStart) { it.plusDays(1) }.takeWhile { it <= end }.toList()
    val data = days.map { totals.today(it) }
    val max = data.maxOrNull()?.coerceAtLeast(1) ?: 1L
    LedgerCard(Modifier.chartGestureRegion()) {
        Text("Daily expenditure", style = MaterialTheme.typography.titleMedium)
        Text("${dateLabel(chartStart)} – ${dateLabel(end)}${if (chartStart > start) " · latest 31 days in period" else ""} · swipe across · MYR", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Bottom) {
            data.forEachIndexed { index, cents ->
                Column(Modifier.widthIn(min = 54.dp).clearAndSetSemantics { contentDescription = "${dateLabel(days[index])}: ${Money.format(cents)}" }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(Money.decimal(cents), style = MaterialTheme.typography.labelSmall)
                    Box(Modifier.height(110.dp).width(22.dp), contentAlignment = Alignment.BottomCenter) {
                        Box(Modifier.fillMaxWidth().height(if (cents > 0) (110 * cents.toDouble() / max).dp else 2.dp)
                            .background(if (cents > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(5.dp)))
                    }
                    Text(days[index].format(DateTimeFormatter.ofPattern("d MMM", Malaysia)), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
