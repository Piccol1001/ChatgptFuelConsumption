package my.fuelledger.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import my.fuelledger.domain.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/** Virtualized dates, fixed bar pitch and symmetric edge space allow any day to be centred. */
@Composable
fun DailySpending(totals: Spending, start: LocalDate, end: LocalDate, today: LocalDate) {
    val first = minOf(start, today.minusDays(15))
    val last = maxOf(end, today.plusDays(15))
    val count = ChronoUnit.DAYS.between(first, last).toInt() + 1
    val todayIndex = ChronoUnit.DAYS.between(first, today).toInt()
    val amounts = remember(totals, first, last) { totals.inRange(first, last).groupBy { totals.date(it.timestamp) }.mapValues { (_, entries) -> entries.sumOf { it.cents } } }
    val maximum = amounts.values.maxOfOrNull { abs(it) }?.coerceAtLeast(1L) ?: 1L
    val negatives = amounts.values.any { it < 0 }
    val list = rememberLazyListState(initialFirstVisibleItemIndex = todayIndex)
    var alignedRange by rememberSaveable { mutableStateOf("") }
    var selectedText by rememberSaveable { mutableStateOf(today.toString()) }
    val selected = LocalDate.parse(selectedText)
    val scope = rememberCoroutineScope()
    val motion = LocalPressMotionEnabled.current
    val privacy = LocalUiPreferences.current.privacyMode
    val rangeKey = "$first/$last/$today"
    LaunchedEffect(rangeKey) {
        if (alignedRange != rangeKey) { list.scrollToItem(todayIndex); selectedText = today.toString(); alignedRange = rangeKey }
    }
    LedgerCard(Modifier.chartGestureRegion()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Daily expenditure", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            TextButton({ selectedText = today.toString(); scope.launch { if (motion) list.animateScrollToItem(todayIndex) else list.scrollToItem(todayIndex) } }, Modifier.testTag("daily-chart-today")) { Text("Today") }
        }
        Text("${dateLabel(selected)} · ${Money.format(amounts[selected] ?: 0L)}", Modifier.testTag("daily-chart-selection"), style = MaterialTheme.typography.titleSmall)
        Text("Swipe to explore · net recorded spending · MYR", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val edge = ((maxWidth - 56.dp) / 2).coerceAtLeast(0.dp)
            LazyRow(state = list, modifier = Modifier.fillMaxWidth().testTag("daily-chart"), contentPadding = PaddingValues(horizontal = edge),
                horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Bottom) {
                items(count, key = { first.plusDays(it.toLong()).toString() }) { index ->
                    val date = first.plusDays(index.toLong())
                    val cents = amounts[date] ?: 0L
                    val barColor = when { date == today -> MaterialTheme.colorScheme.primary; date == selected -> MaterialTheme.colorScheme.secondary; else -> MaterialTheme.colorScheme.outline }
                    Card(onClick = { selectedText = date.toString() }, Modifier.width(56.dp).testTag("daily-bar-$date").semantics {
                        this.selected = date == selected
                        contentDescription = maskAmounts("${dateLabel(date)}: ${Money.format(cents)}${if (date == today) ", Today" else ""}", privacy)
                    }, colors = CardDefaults.cardColors(containerColor = if (date == selected) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(10.dp)) {
                        Column(Modifier.padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(if (date == today) "Today" else date.format(DateTimeFormatter.ofPattern("d MMM", Malaysia)), style = MaterialTheme.typography.labelSmall,
                                textAlign = TextAlign.Center, maxLines = 2, color = if (date == today) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                            Box(Modifier.height(120.dp).width(22.dp)) {
                                val area = if (negatives) 60.dp else 120.dp
                                val height = (area.value * abs(cents).toDouble() / maximum).dp
                                Box(Modifier.fillMaxWidth().height(area), contentAlignment = Alignment.BottomCenter) {
                                    if (cents > 0) Box(Modifier.fillMaxWidth().height(height).background(barColor, RoundedCornerShape(5.dp)))
                                }
                                if (negatives) Box(Modifier.fillMaxWidth().height(area).offset(y = area), contentAlignment = Alignment.TopCenter) {
                                    if (cents < 0) Box(Modifier.fillMaxWidth().height(height).background(MaterialTheme.colorScheme.secondary, RoundedCornerShape(5.dp)))
                                }
                                Box(Modifier.fillMaxWidth().height(1.dp).offset(y = area - 1.dp).background(MaterialTheme.colorScheme.outlineVariant))
                            }
                        }
                    }
                }
            }
        }
    }
}
