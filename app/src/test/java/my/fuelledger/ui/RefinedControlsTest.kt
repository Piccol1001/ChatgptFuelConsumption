package my.fuelledger.ui

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w393dp-h851dp-xxhdpi")
@LooperMode(LooperMode.Mode.PAUSED)
class RefinedControlsTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun journeyPillSupportsSelectionAndHorizontalDragging() {
        compose.setContent {
            var round by remember { mutableStateOf(false) }
            MaterialTheme { JourneyTypeToggle(round) { round = it } }
        }
        compose.onNodeWithText("One Way").assertIsSelected()
        compose.onNodeWithText("Round Trip").performClick().assertIsSelected()
        compose.onNodeWithText("Round Trip").performTouchInput {
            swipe(center, Offset(-width.toFloat() / 2, center.y), durationMillis = 400)
        }
        compose.onNodeWithText("One Way").assertIsSelected()
        compose.onNodeWithText("One Way").performTouchInput {
            swipe(center, Offset(width.toFloat() * 1.5f, center.y), durationMillis = 400)
        }
        compose.onNodeWithText("Round Trip").assertIsSelected()
    }
}
