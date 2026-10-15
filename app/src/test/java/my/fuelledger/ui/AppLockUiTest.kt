package my.fuelledger.ui

import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.window.Dialog
import my.fuelledger.MainActivity
import my.fuelledger.security.AppLockAction
import my.fuelledger.testing.TestLedgerApplication
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowDialog

/** These tests simulate successful auth callbacks; they do not test fingerprint/PIN hardware. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 35], application = TestLedgerApplication::class)
@LooperMode(LooperMode.Mode.PAUSED)
class AppLockUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private fun simulateSuccessfulAuthentication(action: AppLockAction) {
        compose.runOnIdle {
            assertTrue(compose.activity.appLock.beginAuthentication(action))
            compose.activity.appLock.authenticationSucceeded()
        }
        compose.waitUntil(15_000) { !compose.activity.appLock.busy }
        compose.waitForIdle()
    }

    @Test fun existingDialogHidesWithoutLosingUnsubmittedFormStateAndRestoresAfterUnlock() {
        compose.runOnIdle {
            compose.activity.setContent {
                AppLockGate {
                    MaterialTheme {
                        Dialog(onDismissRequest = {}) {
                            PrivacyDialogWindow()
                            var draft by remember { mutableStateOf("") }
                            OutlinedTextField(draft, { draft = it }, label = { Text("Unsaved draft") })
                        }
                    }
                }
            }
        }
        compose.onNode(hasText("Unsaved draft") and hasSetTextAction()).performTextInput("Private unfinished amount")
        val dialog = ShadowDialog.getLatestDialog()
        assertNotNull(dialog)
        simulateSuccessfulAuthentication(AppLockAction.ENABLE)
        compose.runOnIdle { compose.activity.appLock.onBackground() }
        compose.onNodeWithText("Your ledger is locked").assertIsDisplayed()
        compose.runOnIdle {
            assertTrue(dialog.isShowing)
            assertEquals(0f, dialog.window!!.attributes.alpha)
            assertTrue(dialog.window!!.attributes.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE != 0)
            assertTrue(dialog.window!!.attributes.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE != 0)
            assertTrue(compose.activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
            compose.activity.appLock.onForeground()
        }
        simulateSuccessfulAuthentication(AppLockAction.UNLOCK)
        compose.onNodeWithText("Private unfinished amount").assertExists()
        compose.runOnIdle {
            assertEquals(1f, dialog.window!!.attributes.alpha)
            assertTrue(dialog.window!!.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
            assertEquals(0, dialog.window!!.attributes.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
        }
    }

    @Test fun dialogOpenedByPickerCallbackWhileAlreadyLockedIsImmediatelyHidden() {
        val showDialog = mutableStateOf(false)
        compose.runOnIdle {
            compose.activity.setContent {
                AppLockGate {
                    MaterialTheme {
                        Text("Ledger content")
                        if (showDialog.value) Dialog(onDismissRequest = {}) {
                            PrivacyDialogWindow()
                            Text("Sensitive callback result")
                        }
                    }
                }
            }
        }
        simulateSuccessfulAuthentication(AppLockAction.ENABLE)
        compose.runOnIdle { compose.activity.appLock.onBackground() }
        compose.waitForIdle()
        compose.runOnIdle { showDialog.value = true }
        compose.waitForIdle()
        compose.runOnIdle {
            val dialog = ShadowDialog.getLatestDialog()
            assertTrue(dialog.isShowing)
            assertEquals(0f, dialog.window!!.attributes.alpha)
            assertTrue(dialog.window!!.attributes.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE != 0)
        }
        compose.onNodeWithText("Your ledger is locked").assertIsDisplayed()
    }
}
