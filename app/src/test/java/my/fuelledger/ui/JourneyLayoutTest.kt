package my.fuelledger.ui

import android.graphics.Bitmap
import android.os.Looper
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import my.fuelledger.LedgerApplication
import my.fuelledger.MainActivity
import my.fuelledger.data.AppSettings
import my.fuelledger.testing.TestLedgerApplication
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.rules.TestRule
import org.junit.runners.model.Statement
import org.robolectric.RuntimeEnvironment
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import java.io.File
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = TestLedgerApplication::class, qualifiers = "w393dp-h851dp-mdpi")
@LooperMode(LooperMode.Mode.PAUSED)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class JourneyLayoutTest {
    @get:Rule(order = 0) val fontScale = TestRule { base, description ->
        object : Statement() {
            override fun evaluate() {
                RuntimeEnvironment.setFontScale(if (description.methodName == "largeFontScreensRemainAccessible") 1.6f else 1f)
                try { base.evaluate() } finally { RuntimeEnvironment.setFontScale(1f) }
            }
        }
    }
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()
    private fun waitFor(text: String) = compose.waitUntil(30_000) {
        compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    }
    private fun heading(text: String) = compose.onNode(hasText(text) and
        SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
    private fun field(text: String) = compose.onNode(hasText(text) and hasSetTextAction())
    private fun scrollTo(matcher: SemanticsMatcher) = compose.onNode(hasScrollAction() and
        SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)).performScrollToNode(matcher)
    private fun navigate(tab: String) = compose.onNode(hasText(tab) and hasClickAction() and isSelectable()).performClick()
    private fun capture(name: String) {
        compose.waitForIdle()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(32))
        compose.waitForIdle()
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val directory = File("build/reports/journey-layout").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
    }
    private fun compare(theme: String, size: String = "393") {
        waitFor("Overview")
        val repository = (compose.activity.application as LedgerApplication).repository
        runBlocking(Dispatchers.IO) { repository.saveSettings(AppSettings(theme = theme)) }
        compose.waitForIdle()
        val measurements = mutableListOf<String>()
        var headingLeft: Float? = null
        var navTop: Float? = null
        var headingTop: Float? = null
        var headingHeight: Float? = null
        for ((tab, title) in listOf("Home" to "Overview", "Fuel" to "Fuel tracker", "Expenses" to "Expenses", "Journey" to "Journey")) {
            navigate(tab)
            waitFor(title)
            compose.mainClock.advanceTimeBy(500)
            compose.waitForIdle()
            val bounds = heading(title).fetchSemanticsNode().boundsInRoot
            // Compare the same nav item across screens. Different labels may wrap at large fonts.
            val nav = compose.onNode(hasText("Home") and hasClickAction() and isSelectable()).fetchSemanticsNode().boundsInRoot
            if (headingLeft == null) { headingLeft = bounds.left; navTop = nav.top; headingTop = bounds.top; headingHeight = bounds.height }
            assertEquals("Same heading alignment: $tab", headingLeft, bounds.left, .5f)
            assertEquals("Same navigation alignment: $tab", navTop!!, nav.top, .5f)
            if (RuntimeEnvironment.getFontScale() == 1f) assertEquals("Same heading top: $tab", headingTop!!, bounds.top, 1.1f)
            assertEquals("No extra heading line: $tab", headingHeight!!, bounds.height, .5f)
            measurements += "$tab heading=$bounds navigation=$nav"
            capture("$size-$theme-$tab")
        }
        val origin = field("Origin").fetchSemanticsNode().boundsInRoot
        val destination = field("Destination").fetchSemanticsNode().boundsInRoot
        assertEquals("No empty suggestion slots", 14f, destination.top - origin.bottom, .5f)
        val outbound = field("Outbound distance (km)").fetchSemanticsNode().boundsInRoot
        if (RuntimeEnvironment.getFontScale() == 1f) assertEquals("Distance label must not inflate the control", origin.height, outbound.height, .5f)
        assertTrue("Inputs keep accessible touch targets", origin.height >= 48f && outbound.height >= 48f)
        measurements += "Outbound=$outbound"
        measurements += "Origin=$origin Destination=$destination gap=${destination.top - origin.bottom}"
        File("build/reports/journey-layout/$size-$theme-measurements.txt").writeText(measurements.joinToString("\n"))
        // Shared text field geometry can be compared with Expenses without modifying either screen.
        navigate("Expenses")
        scrollTo(hasText("Search descriptions"))
        val reference = field("Search descriptions").fetchSemanticsNode().boundsInRoot
        assertEquals(reference.width, origin.width, .5f)
        assertEquals(reference.height, origin.height, .5f)
    }
    @Test fun darkScreensUseMatchingGeometry() = compare("Dark")
    @Test fun largeFontScreensRemainAccessible() = compare("Light", "393-font160")
    @Test fun lightScreensUseMatchingGeometry() = compare("Light")
    @Test @Config(qualifiers = "w320dp-h640dp-mdpi")
    fun smallScreensUseMatchingGeometry() = compare("Dark", "320")
}
