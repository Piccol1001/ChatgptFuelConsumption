package my.fuelledger.ui

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import my.fuelledger.data.*
import my.fuelledger.domain.*
import java.time.*
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay

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
fun categoryIcon(icon: String): ImageVector = when (icon) {
    "food" -> Icons.Outlined.Restaurant
    "basket" -> Icons.Outlined.ShoppingCart
    "movie" -> Icons.Outlined.Movie
    "fuel" -> Icons.Outlined.LocalGasStation
    "bag" -> Icons.Outlined.ShoppingBag
    "bill" -> Icons.AutoMirrored.Outlined.ReceiptLong
    "health" -> Icons.Outlined.FavoriteBorder
    else -> Icons.Outlined.MoreHoriz
}
@Composable
fun SectionTitle(title: String, subtitle: String? = null) {
    Column(Modifier.padding(vertical = 4.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
@Composable
fun Stat(label: String, value: String, modifier: Modifier = Modifier, detail: String? = null) {
    Surface(modifier, shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            detail?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}
@Composable
fun EmptyState(title: String, detail: String) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Outlined.Inbox, null, tint = MaterialTheme.colorScheme.primary)
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
@Composable
fun CategoryBadge(category: Category?) {
    val color = category?.let { Color(it.color.toULong()) } ?: MaterialTheme.colorScheme.primary
    Box(Modifier.size(44.dp).background(color.copy(alpha = .16f), CircleShape), contentAlignment = Alignment.Center) {
        Icon(categoryIcon(category?.icon.orEmpty()), category?.name, tint = color, modifier = Modifier.size(23.dp))
    }
}
@Composable
fun ExpenseRow(expense: Expense, category: Category?, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CategoryBadge(category)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(expense.description.ifBlank { category?.name ?: "Expense" }, fontWeight = FontWeight.SemiBold)
                Text(category?.name.orEmpty(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(dateTime(expense.timestamp), style = MaterialTheme.typography.labelSmall)
                Text(expense.payment + if (expense.fuelId != null) " · Linked refuel" else "", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
            Text(Money.format(expense.cents), fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
@Composable
fun SpendingBars(title: String, data: List<Pair<String, Long>>, color: Color = MaterialTheme.colorScheme.primary) {
    val max = data.maxOfOrNull { it.second }?.coerceAtLeast(1) ?: 1L
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            data.forEach { (label, value) ->
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        Text(Money.format(value), style = MaterialTheme.typography.labelMedium)
                    }
                    Box(Modifier.fillMaxWidth().height(8.dp).background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(4.dp))) {
                        if (value > 0) Box(Modifier.fillMaxWidth((value.toDouble() / max).toFloat()).fillMaxHeight().background(color, RoundedCornerShape(4.dp)))
                    }
                }
            }
        }
    }
}
