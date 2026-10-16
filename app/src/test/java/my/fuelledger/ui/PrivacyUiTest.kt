package my.fuelledger.ui

import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import my.fuelledger.data.AppSettings

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PrivacyUiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun hiddenAmountsAreMaskedInTextAndChartAccessibility() {
        val settings = AppSettings(privacyMode = true)
        compose.setContent { CompositionLocalProvider(LocalUiPreferences provides settings) { LedgerTheme(true) { Surface { SpendingBars("Test", listOf("Food" to 125050L)) } } } }
        compose.onAllNodesWithText("RM 1,250.50", substring = true, useUnmergedTree = true).assertCountEquals(0)
        compose.onNodeWithContentDescription("Food: RM ••••").assertExists()
    }
}
