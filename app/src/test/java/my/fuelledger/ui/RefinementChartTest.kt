package my.fuelledger.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import my.fuelledger.data.AppSettings
import my.fuelledger.domain.Spending
import my.fuelledger.domain.SpendingPoint
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 35], qualifiers = "w393dp-h851dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RefinementChartTest {
    @get:Rule val compose = createComposeRule()
    private val today = LocalDate.of(2026, 10, 17)
    private fun settle() { compose.mainClock.advanceTimeBy(1000); compose.waitForIdle() }
    private fun centered(date: LocalDate) {
        val bar = compose.onNodeWithTag("daily-bar-$date").fetchSemanticsNode().boundsInRoot
        val viewport = compose.onNodeWithTag("daily-chart").fetchSemanticsNode().boundsInRoot
        assertEquals("$date is at viewport centre", viewport.center.x, bar.center.x, 1f)
    }
    private fun verify(dark: Boolean, width: Int) {
        val zone = ZoneId.of("Asia/Kuala_Lumpur")
        val points = listOf(SpendingPoint(today.atTime(12, 0).atZone(zone).toInstant().toEpochMilli(), 1250, "food"),
            SpendingPoint(today.minusDays(1).atTime(12, 0).atZone(zone).toInstant().toEpochMilli(), -100, "food"))
        compose.setContent { CompositionLocalProvider(LocalUiPreferences provides AppSettings(reducedMotion = true)) { LedgerTheme(dark) {
            Surface { Box(Modifier.width(width.dp)) { DailySpending(Spending(points, zone), today.minusDays(40), today.plusDays(2), today) } }
        } } }
        settle(); centered(today)
        compose.onNodeWithTag("daily-chart-selection").assertTextEquals("17 Oct 2026 · RM 12.50")
        compose.onNodeWithTag("daily-chart").performTouchInput { swipeLeft() }; settle()
        val viewport = compose.onNodeWithTag("daily-chart").fetchSemanticsNode().boundsInRoot
        val current = compose.onAllNodesWithTag("daily-bar-$today").fetchSemanticsNodes().singleOrNull()
        assertTrue(current == null || kotlin.math.abs(current.boundsInRoot.center.x - viewport.center.x) > 1f)
        compose.onNodeWithTag("daily-chart-today").performClick(); settle(); centered(today)
        compose.onNodeWithTag("daily-chart").performScrollToIndex(41); settle()
        compose.onNodeWithTag("daily-bar-${today.plusDays(1)}").performClick()
        compose.onNodeWithTag("daily-chart-selection").assertTextEquals("18 Oct 2026 · RM 0.00")
        compose.onNodeWithTag("daily-chart").performScrollToIndex(0); settle(); centered(today.minusDays(40))
        compose.onNodeWithTag("daily-chart").performScrollToIndex(55); settle(); centered(today.plusDays(15))
        compose.onNodeWithTag("daily-chart-today").performClick(); settle(); centered(today)
    }
    @Test fun lightChartCentersTodayScrollsReentersAndAllowsEdgeSelection() = verify(false, 393)
    @Test fun narrowDarkChartUsesSameCenterAndZeroFutureDays() = verify(true, 320)
}
