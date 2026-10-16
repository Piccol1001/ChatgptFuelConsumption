package my.fuelledger.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import my.fuelledger.R
import my.fuelledger.data.*
import my.fuelledger.domain.*
import java.time.*
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay

// Shared main-screen insets; the app Scaffold owns system bars and navigation.
internal val ledgerScreenPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 28.dp)

// One calendar source updates a long-running dashboard after midnight or a time-zone change.
data class CalendarState(val today: LocalDate, val zone: ZoneId)
@Composable
fun currentCalendar(): CalendarState {
    val clock by produceState(CalendarState(LocalDate.now(), ZoneId.systemDefault())) {
        while (true) { delay(60_000); value = CalendarState(LocalDate.now(), ZoneId.systemDefault()) }
    }
    return clock
}

fun number(value: Double, decimals: Int = 2) = String.format(Malaysia, "%.${decimals}f", value)
fun localDate(timestamp: Long): LocalDate = Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).toLocalDate()
fun dateTime(timestamp: Long) = Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault())
    .format(DateTimeFormatter.ofPattern("d MMM yyyy · h:mm a", Malaysia))
fun dateLabel(date: LocalDate) = date.format(DateTimeFormatter.ofPattern("d MMM yyyy", Malaysia))
/** Official Material Symbols Outlined, bundled as small native vector resources. */
@Composable
fun ledgerSymbol(name: String): ImageVector = ImageVector.vectorResource(when (name.lowercase()) {
    "home", "dashboard" -> R.drawable.symbol_home
    "fuel", "local_gas_station" -> R.drawable.symbol_local_gas_station
    "expenses", "wallet", "account_balance_wallet" -> R.drawable.symbol_account_balance_wallet
    "analytics", "monitoring" -> R.drawable.symbol_monitoring
    "food", "restaurant" -> R.drawable.symbol_restaurant
    "basket", "shopping_cart" -> R.drawable.symbol_shopping_cart
    "movie", "entertainment" -> R.drawable.symbol_movie
    "bag", "shopping_bag" -> R.drawable.symbol_shopping_bag
    "bill", "receipt", "receipt_long" -> R.drawable.symbol_receipt_long
    "health", "favorite" -> R.drawable.symbol_favorite
    "add" -> R.drawable.symbol_add
    "route" -> R.drawable.symbol_route
    "speed" -> R.drawable.symbol_speed
    "tune" -> R.drawable.symbol_tune
    "settings" -> R.drawable.symbol_settings
    else -> R.drawable.symbol_more_horiz
})

@Composable
fun categoryIcon(icon: String): ImageVector = ledgerSymbol(icon)


@Composable
fun LedgerCard(modifier: Modifier = Modifier, emphasized: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp),
        color = if (emphasized) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, if (emphasized) MaterialTheme.colorScheme.primary.copy(alpha = .18f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = .55f))) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp), content = content)
    }
}

/** Stack cards/actions instead of truncating figures at narrow widths or larger font settings. */
@Composable
fun ResponsivePair(first: @Composable (Modifier) -> Unit, second: @Composable (Modifier) -> Unit) {
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth < 340.dp || fontScale >= 1.3f) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { first(Modifier.fillMaxWidth()); second(Modifier.fillMaxWidth()) }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { first(Modifier.weight(1f)); second(Modifier.weight(1f)) }
        }
    }
}

@Composable
fun SectionTitle(title: String, subtitle: String? = null) {
    Column(Modifier.padding(top = 4.dp, bottom = 2.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
        if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun ScreenHeading(title: String, subtitle: String, icon: ImageVector, openSettings: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(46.dp).background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(15.dp)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (openSettings != null) IconButton(onClick = openSettings) { Icon(ledgerSymbol("settings"), "Settings") }
    }
}

@Composable
fun HeroAmount(label: String, value: String, detail: String, icon: ImageVector = ledgerSymbol("expenses")) {
    LedgerCard(emphasized = true) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.weight(1f))
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        }
        Text(value, style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.onSurface)
        Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}

@Composable
fun Stat(label: String, value: String, modifier: Modifier = Modifier, detail: String? = null) {
    Surface(modifier, shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .45f))) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleLarge)
            detail?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
fun LedgerAction(label: String, icon: ImageVector, modifier: Modifier = Modifier, secondary: Boolean = false, onClick: () -> Unit) {
    val content: @Composable RowScope.() -> Unit = {
        Icon(icon, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text(label)
    }
    if (secondary) FilledTonalButton(onClick, modifier.heightIn(min = 56.dp), shape = RoundedCornerShape(16.dp), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp), content = content)
    else Button(onClick, modifier.heightIn(min = 56.dp), shape = RoundedCornerShape(16.dp), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp), content = content)
}

@Composable
fun EmptyState(title: String, detail: String) {
    LedgerCard {
        Icon(Icons.Outlined.Inbox, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun categoryTint(category: Category?): Color {
    val background = MaterialTheme.colorScheme.surfaceContainerLow.luminance()
    val target = if (background < .4f) Color.White else Color.Black
    var tint = category?.let { Color(it.color.toInt()) } ?: MaterialTheme.colorScheme.primary
    // Keep saved category hues while making icons readable in either appearance.
    repeat(8) {
        val foreground = tint.luminance()
        val contrast = (maxOf(background, foreground) + .05f) / (minOf(background, foreground) + .05f)
        if (contrast < 3f) tint = lerp(tint, target, .15f)
    }
    return tint
}

@Composable
fun CategoryBadge(category: Category?) {
    val color = categoryTint(category)
    Box(Modifier.size(44.dp).background(color.copy(alpha = .13f), RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
        Icon(categoryIcon(category?.icon.orEmpty()), null, tint = color, modifier = Modifier.size(23.dp))
    }
}

@Composable
fun ExpenseRow(expense: Expense, category: Category?, onClick: () -> Unit) {
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val stacked = maxWidth < 330.dp || fontScale >= 1.3f
        Card(onClick = onClick, modifier = Modifier.fillMaxWidth().heightIn(min = 88.dp), shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CategoryBadge(category)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(expense.description.ifBlank { category?.name ?: "Expense" }, style = MaterialTheme.typography.titleSmall)
                    Text(category?.name ?: "Expense", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(dateTime(expense.timestamp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text((expensePaymentGroup(expense.payment) ?: expense.payment) + if (expense.fuelId != null) " · Linked refuel" else "", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                    if (stacked) Text(Money.format(expense.cents), style = MaterialTheme.typography.titleMedium)
                }
                if (!stacked) Text(Money.format(expense.cents), style = MaterialTheme.typography.titleSmall, modifier = Modifier.widthIn(max = 145.dp))
            }
        }
    }
}

@Composable
fun SpendingBars(title: String, data: List<Pair<String, Long>>, color: Color = MaterialTheme.colorScheme.primary) {
    val privacy = LocalUiPreferences.current.privacyMode
    val max = data.maxOfOrNull { it.second }?.coerceAtLeast(1) ?: 1L
    LedgerCard(Modifier.chartGestureRegion()) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        data.forEach { (label, value) ->
            Column(verticalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.clearAndSetSemantics { contentDescription = maskAmounts("$label: ${Money.format(value)}", privacy) }) {
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                    Text(Money.format(value), style = MaterialTheme.typography.labelMedium)
                }
                Box(Modifier.fillMaxWidth().height(7.dp).background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(4.dp))) {
                    if (value > 0) Box(Modifier.fillMaxWidth((value.toDouble() / max).toFloat()).fillMaxHeight().background(color, RoundedCornerShape(4.dp)))
                }
            }
        }
    }
}

@Composable
fun FuelTrend(data: List<Pair<String, Double>>, description: String = "Per-fill estimate · km/L") {
    val accent = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outlineVariant
    val values = data.takeLast(8)
    val low = values.minOfOrNull { it.second } ?: 0.0
    val high = values.maxOfOrNull { it.second } ?: 1.0
    val padding = ((high - low) * .2).coerceAtLeast(.5)
    val bottom = (low - padding).coerceAtLeast(0.0)
    val top = high + padding
    LedgerCard(Modifier.chartGestureRegion()) {
        Text("Consumption trend", style = MaterialTheme.typography.titleMedium)
        Text("$description · latest ${values.size}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (values.isEmpty()) Text("Complete a valid fuel interval to start your trend.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        else {
            Row(Modifier.fillMaxWidth().height(152.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) {
                    Text(number(top, 1), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(number(bottom, 1), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Canvas(Modifier.weight(1f).fillMaxHeight().padding(vertical = 8.dp).clearAndSetSemantics {
                    contentDescription = "Fuel efficiency trend. " + values.joinToString { "${it.first}: ${number(it.second)} km per litre" }
                }) {
                    val inset = 5.dp.toPx()
                    val chartWidth = size.width - inset * 2
                    val chartHeight = size.height - inset * 2
                    repeat(3) { index -> val y = inset + chartHeight * index / 2; drawLine(grid, Offset(inset, y), Offset(size.width - inset, y), strokeWidth = 1.dp.toPx()) }
                    val points = values.mapIndexed { i, item ->
                        Offset(if (values.size == 1) size.width / 2 else inset + chartWidth * i / (values.size - 1), inset + chartHeight * (1 - (item.second - bottom) / (top - bottom)).toFloat())
                    }
                    if (points.size > 1) {
                        val area = Path().apply {
                            moveTo(points.first().x, size.height - inset)
                            points.forEach { lineTo(it.x, it.y) }
                            lineTo(points.last().x, size.height - inset); close()
                        }
                        drawPath(area, accent.copy(alpha = .08f))
                        val line = Path().apply { moveTo(points.first().x, points.first().y); points.drop(1).forEach { lineTo(it.x, it.y) } }
                        drawPath(line, accent, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))
                    }
                    points.forEach { drawCircle(accent, 4.dp.toPx(), it) }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(values.first().first, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (values.size > 1) Text(values.last().first, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("Latest ${number(values.last().second)} km/L", style = MaterialTheme.typography.labelLarge, color = accent)
        }
    }
}

@Composable
fun CategoryDonut(distribution: List<Pair<Category?, Long>>, total: Long) {
    val privacy = LocalUiPreferences.current.privacyMode
    val colors = distribution.map { categoryTint(it.first) }
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    LedgerCard(Modifier.chartGestureRegion()) {
        Text("Where your money goes", style = MaterialTheme.typography.titleMedium)
        Box(Modifier.align(Alignment.CenterHorizontally).size(188.dp).clearAndSetSemantics {
            contentDescription = "Category spending. " + distribution.joinToString { maskAmounts("${it.first?.name ?: "Other"}: ${Money.format(it.second)}, ${number(if (total > 0) it.second * 100.0 / total else 0.0, 1)} percent", privacy) }
        }, contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize().padding(12.dp)) {
                val stroke = 19.dp.toPx()
                val inset = stroke / 2
                val arcSize = Size(size.width - stroke, size.height - stroke)
                drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
                var start = -90f
                distribution.forEachIndexed { index, (_, cents) ->
                    val sweep = if (total > 0) (cents * 360.0 / total).toFloat() else 0f
                    if (sweep > 0f) drawArc(colors[index], start + .8f, (sweep - 1.6f).coerceAtLeast(.2f), false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Butt))
                    start += sweep
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(distribution.size.toString(), style = MaterialTheme.typography.headlineMedium)
                Text("categories", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        distribution.forEachIndexed { index, (category, cents) ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(9.dp).background(colors[index], CircleShape))
                Text(category?.name ?: "Other", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                Text("${number(if (total > 0) cents * 100.0 / total else 0.0, 1)}%", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (distribution.isEmpty()) Text("Your category chart appears after you add an expense.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
