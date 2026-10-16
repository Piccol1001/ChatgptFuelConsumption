package my.fuelledger.ui

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal val LocalPressMotionEnabled = staticCompositionLocalOf { true }

/** Apply inside MaterialTheme, which otherwise replaces LocalIndication with its own ripple. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PremiumInteractions(content: @Composable () -> Unit) {
    val inspection = LocalInspectionMode.current
    val resolver = LocalContext.current.contentResolver
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    fun systemMotionEnabled() = inspection || Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
    var systemMotion by remember(resolver) { mutableStateOf(systemMotionEnabled()) }
    DisposableEffect(resolver, lifecycle, inspection) {
        if (inspection) return@DisposableEffect onDispose {}
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { systemMotion = systemMotionEnabled() }
        }
        resolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer)
        val resume = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) systemMotion = systemMotionEnabled() }
        lifecycle.addObserver(resume)
        onDispose { resolver.unregisterContentObserver(observer); lifecycle.removeObserver(resume) }
    }
    val durationScale = rememberCoroutineScope().coroutineContext[MotionDurationScale]?.scaleFactor ?: 1f
    val motion = systemMotion && durationScale > 0f
    val accent = MaterialTheme.colorScheme.primary
    // Keep indication nodes (and their keyboard focus) across theme/motion changes.
    val currentMotion = rememberUpdatedState(motion)
    val currentAccent = rememberUpdatedState(accent)
    val indication = remember { ScalePressIndication(currentMotion, currentAccent) }
    CompositionLocalProvider(LocalRippleConfiguration provides null, LocalIndication provides indication,
        LocalPressMotionEnabled provides motion, content = content)
}

/** Scale drawing only: layout, pointer hit regions, focus and semantics keep their original bounds. */
@Composable
internal fun Modifier.pressFeedback(source: MutableInteractionSource, shape: Shape? = null): Modifier {
    val pressed by source.collectIsPressedAsState()
    val dragged by source.collectIsDraggedAsState()
    val focused = source.collectIsFocusedAsState()
    val motion = LocalPressMotionEnabled.current
    val active = pressed || dragged
    val scale = animateFloatAsState(if (motion && active) .97f else 1f,
        tween(if (active) 90 else 130), label = "Press scale")
    val accent = MaterialTheme.colorScheme.primary
    return drawWithContent {
        val amount = if (motion) scale.value else 1f
        val contentScope = this
        scale(amount, amount) {
            contentScope.drawContent()
            if (focused.value) drawAccentFocus(accent, shape)
        }
    }
}

private fun DrawScope.drawAccentFocus(accent: Color, shape: Shape? = null) {
    val inset = 1.dp.toPx()
    val area = Size((size.width - inset * 2).coerceAtLeast(0f), (size.height - inset * 2).coerceAtLeast(0f))
    if (area.width > 0f && area.height > 0f) translate(inset, inset) {
        val outline = (shape ?: RoundedCornerShape(16.dp)).createOutline(area, layoutDirection, this)
        drawOutline(outline, accent, style = Stroke(2.dp.toPx()))
    }
}

/** Foundation selectable/clickable controls use this fallback; no grey paint is ever drawn. */
private class ScalePressIndication(val motion: State<Boolean>, val accent: State<Color>) : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode = ScalePressNode(interactionSource, motion, accent)
    override fun equals(other: Any?) = this === other
    override fun hashCode() = System.identityHashCode(this)
}

private class ScalePressNode(private val source: InteractionSource, private val motion: State<Boolean>,
    private val accent: State<Color>) : Modifier.Node(), DrawModifierNode {
    private val scale = Animatable(1f)
    private val presses = mutableSetOf<PressInteraction.Press>()
    private var focused by mutableStateOf(false)
    private var animation: Job? = null

    override fun onAttach() {
        coroutineScope.launch {
            scale.snapTo(1f)
            source.interactions.collect { interaction ->
                when (interaction) {
                    is PressInteraction.Press -> presses.add(interaction)
                    is PressInteraction.Release -> presses.remove(interaction.press)
                    is PressInteraction.Cancel -> presses.remove(interaction.press)
                    is FocusInteraction.Focus -> focused = true
                    is FocusInteraction.Unfocus -> focused = false
                    else -> return@collect
                }
                animation?.cancel()
                animation = coroutineScope.launch {
                    if (motion.value) scale.animateTo(if (presses.isNotEmpty()) .97f else 1f, tween(if (presses.isNotEmpty()) 90 else 130))
                    else scale.snapTo(1f)
                }
            }
        }
    }

    override fun onDetach() { presses.clear(); focused = false; animation?.cancel() }

    override fun ContentDrawScope.draw() {
        val amount = if (motion.value) scale.value else 1f
        val contentScope = this
        scale(amount, amount) {
            contentScope.drawContent()
            if (focused) drawAccentFocus(accent.value)
        }
    }
}
