package my.fuelledger.ui

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Velocity

internal data class MainDestination(val key: String, val label: String, val symbol: String)
internal val mainDestinations = listOf(
    MainDestination("Home", "Home", "home"), MainDestination("Fuel", "Fuel", "fuel"),
    MainDestination("Expenses", "Expenses", "expenses"), MainDestination("Journey", "Journey", "route"),
    MainDestination("Analytics", "Analysis", "analytics"),
)

// Nested child scrollers own their entire gesture, including overflow/flings at either edge.
// Consume only unused horizontal deltas here, before they reach Pager's scrolling ancestor.
// Direct page swipes still use Foundation's native touch slop, drag and snap handling.
private object ChildScrollBoundary : NestedScrollConnection {
    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource) = Offset(available.x, 0f)
    override suspend fun onPostFling(consumed: Velocity, available: Velocity) = Velocity(available.x, 0f)
}

@Composable
internal fun MainPager(state: PagerState, enabled: Boolean, content: @Composable (Int) -> Unit) {
    HorizontalPager(
        state = state, modifier = Modifier.fillMaxSize().testTag("main-pager"),
        key = { mainDestinations[it].key }, userScrollEnabled = enabled,
        // Keep physical left/right behavior consistent with the requested order in RTL, too.
        reverseLayout = LocalLayoutDirection.current == LayoutDirection.Rtl,
        pageNestedScrollConnection = ChildScrollBoundary,
    ) { page ->
        val active = page == state.currentPage
        // Neighbors are visible while dragging, but only the current page exposes controls.
        Box(Modifier.fillMaxSize().testTag("main-page-${mainDestinations[page].key}")
            .focusProperties { canFocus = active }
            .then(if (active) Modifier else Modifier.clearAndSetSemantics {})) { content(page) }
    }
}

/** Static charts reserve horizontal gestures locally; vertical list scrolling remains native. */
internal fun Modifier.chartGestureRegion(): Modifier = pointerInput(Unit) {
    detectHorizontalDragGestures { change, _ -> change.consume() }
}
