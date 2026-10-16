package my.fuelledger.ui

import android.app.Application
import android.graphics.Bitmap
import android.os.Looper
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 35], application = Application::class, qualifiers = "w393dp-h851dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class PressFeedbackTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val marker = Color(0xFF168F80)

    private data class PaintedBounds(val left: Int, val top: Int, val right: Int, val bottom: Int) {
        val width get() = right - left + 1
    }

    private fun Bitmap.isMarker(x: Int, y: Int): Boolean {
        val pixel = getPixel(x, y)
        return kotlin.math.abs(android.graphics.Color.red(pixel) - 0x16) <= 2 &&
            kotlin.math.abs(android.graphics.Color.green(pixel) - 0x8F) <= 2 &&
            kotlin.math.abs(android.graphics.Color.blue(pixel) - 0x80) <= 2
    }

    private fun Bitmap.paintedBounds(): PaintedBounds {
        var left = width
        var right = -1
        var top = height
        var bottom = -1
        for (y in 0 until height) for (x in 0 until width) if (isMarker(x, y)) {
            left = minOf(left, x)
            right = maxOf(right, x)
            top = minOf(top, y)
            bottom = maxOf(bottom, y)
        }
        assertTrue("The component should retain its original accent colour", right >= left && bottom >= top)
        return PaintedBounds(left, top, right, bottom)
    }

    private fun capture(stage: String): Bitmap {
        compose.waitForIdle()
        // The manually advanced Compose clock does not drive Android's native draw frame.
        // Drain that frame before copying pixels, especially after switching the theme.
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(32))
        compose.waitForIdle()
        return compose.onNodeWithTag(stage).captureToImage().asAndroidBitmap()
    }

    private inline fun withAnimatorScale(scale: Float, block: () -> Unit) {
        val resolver = compose.activity.contentResolver
        val original = Settings.Global.getString(resolver, Settings.Global.ANIMATOR_DURATION_SCALE)
        Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, scale)
        try { block() } finally {
            Settings.Global.putString(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, original)
        }
    }

    @Composable
    private fun Stages(onClick: () -> Unit) {
        val shape = RoundedCornerShape(20.dp)
        Column {
            Box(Modifier.size(320.dp, 128.dp).background(MaterialTheme.colorScheme.surface).testTag("button-stage"),
                contentAlignment = Alignment.Center) {
                Button(onClick, Modifier.size(240.dp, 80.dp).testTag("button"), shape = shape,
                    colors = ButtonDefaults.buttonColors(containerColor = marker)) {}
            }
            Box(Modifier.size(320.dp, 128.dp).background(MaterialTheme.colorScheme.surface).testTag("raw-stage"),
                contentAlignment = Alignment.Center) {
                // Category options paint their rounded accent on a child of the selectable target.
                Box(Modifier.size(240.dp, 80.dp).testTag("raw").clickable(onClick = onClick)) {
                    Box(Modifier.fillMaxSize().clip(shape).background(marker))
                }
            }
        }
    }

    @Test fun heldControlsShrinkWithoutGreyOverlayInLightTheme() = assertHeldFeedback(dark = false)

    @Test fun heldControlsShrinkWithoutGreyOverlayInDarkTheme() = assertHeldFeedback(dark = true)

    private fun assertHeldFeedback(dark: Boolean) = withAnimatorScale(1f) {
        var clicks = 0
        compose.setContent {
            MaterialTheme(colorScheme = if (dark) LedgerDarkColors else LedgerLightColors) {
                PremiumInteractions { Stages { clicks++ } }
            }
        }
        compose.mainClock.autoAdvance = false
        for (target in listOf("button", "raw")) {
            val node = compose.onNodeWithTag(target)
            node.assertHasClickAction().assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
            val touchBounds = node.fetchSemanticsNode().boundsInRoot
            val idle = capture("$target-stage")
            assertEquals("The native draw must reflect the current theme before pressing",
                (if (dark) LedgerDarkColors else LedgerLightColors).surface.toArgb(), idle.getPixel(0, 0))
            val idlePaint = idle.paintedBounds()
            val clicksBeforePress = clicks
            node.performTouchInput { down(center) }
            compose.mainClock.advanceTimeBy(240)
            assertEquals("A held press must not invoke the click", clicksBeforePress, clicks)
            val held = capture("$target-stage")
            val heldPaint = held.paintedBounds()
            assertEquals("Pressed visuals should shrink gently", .97,
                heldPaint.width.toDouble() / idlePaint.width, .012)
            assertTrue("The held centre must keep its accent colour, without a grey ripple",
                held.isMarker(held.width / 2, held.height / 2))
            assertEquals("Rounded corners must not acquire a square pressed overlay",
                idle.getPixel(idlePaint.left + 1, idlePaint.top + 1),
                held.getPixel(idlePaint.left + 1, idlePaint.top + 1))
            assertEquals("Visual scaling must preserve the accessible touch bounds", touchBounds,
                node.fetchSemanticsNode().boundsInRoot)
            node.performTouchInput { up() }
            compose.mainClock.advanceTimeBy(300)
            assertEquals("The original size must return after release", idlePaint, capture("$target-stage").paintedBounds())
        }
        assertEquals(2, clicks)
    }

    @Test fun releaseCancellationDisabledStateAndSelectedSemanticsRemainIntact() {
        var clicks = 0
        var disabledClicks = 0
        compose.setContent {
            var selected by remember { mutableStateOf(false) }
            var destination by remember { mutableStateOf(0) }
            MaterialTheme {
                PremiumInteractions {
                    Column {
                        Button({ clicks++ }, Modifier.heightIn(min = 48.dp).testTag("action")) { Text("Save") }
                        Button({ disabledClicks++ }, Modifier.heightIn(min = 48.dp).testTag("disabled"), enabled = false) { Text("Unavailable") }
                        FilterChip(selected, { selected = !selected }, label = { Text("Food") }, modifier = Modifier.heightIn(min = 48.dp).testTag("category"))
                        NavigationBar {
                            NavigationBarItem(destination == 0, { destination = 0 }, icon = { Text("H") },
                                label = { Text("Home") }, modifier = Modifier.testTag("home"))
                            NavigationBarItem(destination == 1, { destination = 1 }, icon = { Text("E") },
                                label = { Text("Expenses") }, modifier = Modifier.testTag("expenses"))
                        }
                    }
                }
            }
        }
        val action = compose.onNodeWithTag("action")
        action.assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        action.performTouchInput { down(center) }
        compose.runOnIdle { assertEquals(0, clicks) }
        action.performTouchInput { up() }
        compose.runOnIdle { assertEquals(1, clicks) }
        action.performTouchInput { down(center); cancel() }
        compose.runOnIdle { assertEquals("Cancelled presses must not save", 1, clicks) }
        compose.onNodeWithTag("disabled").assertIsNotEnabled().performTouchInput { click() }
        compose.runOnIdle { assertEquals(0, disabledClicks) }
        compose.onNodeWithTag("category").assertIsNotSelected().performTouchInput { click() }
        compose.onNodeWithTag("category").assertIsSelected().assertHasClickAction()
        compose.onNodeWithTag("home").assertIsSelected()
        compose.onNodeWithTag("expenses").performTouchInput { click() }
        compose.onNodeWithTag("expenses").assertIsSelected().assertHasClickAction()
        compose.onNodeWithTag("home").assertIsNotSelected()
    }

    @Test fun systemReducedMotionKeepsPressedVisualsStationaryAndClicksFunctional() = withAnimatorScale(0f) {
        var clicks = 0
        compose.setContent { MaterialTheme { PremiumInteractions { Stages { clicks++ } } } }
        compose.mainClock.autoAdvance = false
        for (target in listOf("button", "raw")) {
            val idle = capture("$target-stage").paintedBounds()
            val node = compose.onNodeWithTag(target)
            node.performTouchInput { down(center) }
            compose.mainClock.advanceTimeBy(300)
            assertEquals("Disabling system animations must also disable press scaling", idle,
                capture("$target-stage").paintedBounds())
            node.performTouchInput { up() }
            compose.mainClock.advanceTimeBy(300)
        }
        compose.runOnIdle { assertEquals(2, clicks) }
    }
}
