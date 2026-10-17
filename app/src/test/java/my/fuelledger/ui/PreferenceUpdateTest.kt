package my.fuelledger.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import my.fuelledger.LedgerApplication
import my.fuelledger.LedgerViewModel
import my.fuelledger.MainActivity
import my.fuelledger.testing.TestLedgerApplication
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 35], application = TestLedgerApplication::class, qualifiers = "w393dp-h851dp-mdpi")
@LooperMode(LooperMode.Mode.PAUSED)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PreferenceUpdateTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun preferenceWriteDoesNotDisableUnrelatedControlsOrStartGlobalLoading() {
        compose.waitUntil(30_000) { compose.onAllNodesWithText("Overview").fetchSemanticsNodes().isNotEmpty() }
        val vm = ViewModelProvider(compose.activity)[LedgerViewModel::class.java]
        compose.waitUntil(10_000) { org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle(); !vm.busy.value }
        compose.runOnUiThread {
            vm.saveSettings(vm.state.value.settings.copy(keepReceiptPhotos = false)) {}
            assertFalse("Preference writes must not toggle the global transaction busy state", vm.busy.value)
        }
        compose.waitUntil(10_000) { org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle(); !vm.state.value.settings.keepReceiptPhotos }
        val repo = (compose.activity.application as LedgerApplication).repository
        assertFalse(runBlocking(Dispatchers.IO) { repo.backup().settings.keepReceiptPhotos })
        // Two queued changes must merge with current persisted preferences, not stale copies.
        compose.runOnUiThread {
            vm.updateSettings({ it.copy(widgetPrivacy = false) })
            vm.updateSettings({ it.copy(receiptSaveMode = "Smart") })
            assertFalse(vm.busy.value)
        }
        compose.waitUntil(10_000) { org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle(); vm.state.value.settings.receiptSaveMode == "Smart" && !vm.state.value.settings.widgetPrivacy }
        val saved = runBlocking(Dispatchers.IO) { repo.backup().settings }
        assertFalse(saved.keepReceiptPhotos); assertFalse(saved.widgetPrivacy); assertEquals("Smart", saved.receiptSaveMode)
    }
}
