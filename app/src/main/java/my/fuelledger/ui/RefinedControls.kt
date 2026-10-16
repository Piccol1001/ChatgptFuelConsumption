package my.fuelledger.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import my.fuelledger.data.AllocationCategory

@Composable
fun ExpandableHelp(title: String = "How it works", content: @Composable () -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    TextButton(onClick = { open = !open }) { Icon(Icons.Outlined.Info, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(if (open) "Hide $title" else title) }
    if (open) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
}

@Composable
fun JourneyTypeToggle(roundTrip: Boolean, change: (Boolean) -> Unit) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val interaction = rememberAppLockInteraction()
    val latestInteraction by rememberUpdatedState(interaction)
    val latestChange by rememberUpdatedState(change)
    val latestRoundTrip by rememberUpdatedState(roundTrip)
    var dragging by remember { mutableStateOf<Float?>(null) }
    val shape = RoundedCornerShape(28.dp)
    BoxWithConstraints(Modifier.fillMaxWidth().heightIn(min = 56.dp)
        .clip(shape).background(MaterialTheme.colorScheme.surfaceContainerHighest, shape).selectableGroup()
        .pointerInput(rtl) {
            detectHorizontalDragGestures(onDragStart = { point ->
                dragging = ((point.x - size.width / 4f) / (size.width / 2f)).coerceIn(0f, 1f)
            }, onHorizontalDrag = { event, delta ->
                event.consume(); dragging = ((dragging ?: if (latestRoundTrip != rtl) 1f else 0f) + delta / (size.width / 2f)).coerceIn(0f, 1f)
            }, onDragEnd = {
                dragging?.let { if (latestInteraction()) latestChange((it >= .5f) != rtl) }; dragging = null
            }, onDragCancel = { dragging = null })
        }) {
        val half = maxWidth / 2
        val fraction = dragging ?: if (roundTrip != rtl) 1f else 0f
        val animated by animateDpAsState(half * fraction, tween(180), label = "Journey type thumb")
        Box(Modifier.matchParentSize()) {
            Box(Modifier.absoluteOffset(x = if (dragging != null) half * fraction else animated)
                .width(half).fillMaxHeight().padding(4.dp).background(MaterialTheme.colorScheme.primaryContainer, shape))
        }
        Row(Modifier.fillMaxWidth()) {
            listOf(false to "One Way", true to "Round Trip").forEach { (value, label) ->
                Box(Modifier.weight(1f).heightIn(min = 56.dp).selectable(selected = roundTrip == value,
                    onClick = { if (interaction()) change(value) }, role = Role.RadioButton).padding(horizontal = 8.dp, vertical = 14.dp), contentAlignment = Alignment.Center) {
                    Text(label, style = MaterialTheme.typography.labelLarge,
                        color = if (roundTrip == value) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
fun AllocationDonut(categories: List<AllocationCategory>) {
    val colors = listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary, MaterialTheme.colorScheme.tertiary,
        MaterialTheme.colorScheme.error, MaterialTheme.colorScheme.onSurfaceVariant)
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(164.dp).clearAndSetSemantics {
            contentDescription = "Monthly allocation: " + categories.joinToString { "${it.name} ${percentText(it.basisPoints)} percent" }
        }, contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize().padding(10.dp)) {
                val stroke = 18.dp.toPx(); val inset = stroke / 2
                val area = Size(size.width - stroke, size.height - stroke)
                drawArc(track, 0f, 360f, false, Offset(inset, inset), area, style = Stroke(stroke))
                var start = -90f
                categories.forEachIndexed { index, category ->
                    val sweep = category.basisPoints * .036f
                    if (sweep > 0) drawArc(colors[index % colors.size], start, sweep, false, Offset(inset, inset), area, style = Stroke(stroke))
                    start += sweep
                }
            }
            Text("100%", style = MaterialTheme.typography.headlineMedium)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            categories.forEachIndexed { index, category ->
                Text("● ${category.name}", color = colors[index % colors.size], style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
