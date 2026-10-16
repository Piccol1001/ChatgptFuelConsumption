package my.fuelledger.ui

import android.content.Context
import android.content.ContextWrapper
import android.view.View
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.Window
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindowProvider
import my.fuelledger.MainActivity
import my.fuelledger.security.AppLockAction
import my.fuelledger.security.AppLockController
import my.fuelledger.security.AppLockTimeout

private val LocalAppLock = staticCompositionLocalOf<AppLockController?> { null }
// Dynamic local reads invalidate existing separate Dialog compositions on every lock transition.
val LocalAppLocked = compositionLocalOf { false }

/** Counts soft-keyboard edits, which do not dispatch touch/key events to an activity. */
@Composable
fun rememberAppLockInteraction(): () -> Boolean {
    val controller = LocalAppLock.current
    return remember(controller) { {
        controller?.onInteraction()
        controller?.locked != true && controller?.settingsUnavailable != true
    } }
}

/** Keep forms/picker callbacks alive while hiding and disabling all underlying UI. */
@Composable
fun AppLockGate(theme: String = "System", content: @Composable () -> Unit) {
    val activity = LocalContext.current.mainActivity()
    val controller = activity?.appLock
    if (controller == null) { content(); return }
    val blocked = controller.locked || controller.settingsUnavailable
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(blocked) { if (blocked) { focus.clearFocus(force = true); keyboard?.hide() } }
    CompositionLocalProvider(LocalAppLock provides controller, LocalAppLocked provides blocked) {
      Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = if (blocked) 0f else 1f }
            .focusProperties { canFocus = !blocked }
            .then(if (blocked) Modifier.clearAndSetSemantics { } else Modifier)) { content() }
        if (blocked) LedgerTheme(dark = when (theme) { "Dark" -> true; "Light" -> false; else -> androidx.compose.foundation.isSystemInDarkTheme() }) {
            Surface(Modifier.fillMaxSize().pointerInput(Unit) {
                // Register a hit target over blank space as well as the Unlock button.
                // Underlying transparent ledger nodes must never receive a touch.
                awaitPointerEventScope { while (true) awaitPointerEvent(PointerEventPass.Final) }
            }) {
                Column(Modifier.fillMaxSize().safeDrawingPadding().padding(28.dp),
                    verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Outlined.Lock, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(20.dp))
                    Text("Your ledger is locked", style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(12.dp))
                    Text("Use your fingerprint or device screen lock to continue. Authentication stays on this device.",
                        style = MaterialTheme.typography.bodyMedium)
                    controller.error?.let { Spacer(Modifier.height(16.dp)); Text(it, color = MaterialTheme.colorScheme.error) }
                    Spacer(Modifier.height(24.dp))
                    if (controller.settingsUnavailable) {
                        Button(onClick = controller::reload) { Text("Retry private settings") }
                    } else Button(onClick = { activity.authenticateAppLock(AppLockAction.UNLOCK) }, enabled = !controller.busy) {
                        if (controller.busy) { CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp); Spacer(Modifier.width(10.dp)) }
                        Text(if (controller.busy) "Waiting for authentication…" else "Unlock")
                    }
                }
            }
        }
      }
      BackHandler(blocked) { /* Registered after ledger handlers so locked Back cannot change its state. */ }
    }
}

/**
 * Call inside every app Dialog/AlertDialog. Dialogs use separate Android windows;
 * hiding only their parent Compose node cannot protect them. Changing the window
 * rather than disposing its composition retains edits and in-flight picker state.
 * A dialog opened by a background callback is hidden in the same composition.
 */
@Composable
fun PrivacyDialogWindow() {
    val locked = LocalAppLocked.current
    val controller = LocalAppLock.current
    val view = LocalView.current
    val window = (view.parent as? DialogWindowProvider)?.window
    val originalFlags = remember(window) { window?.attributes?.flags ?: 0 }
    val originalAccessibility = remember(view) { view.importantForAccessibility }
    SideEffect {
        window?.let {
            it.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            val attributes = it.attributes
            attributes.alpha = if (locked) 0f else 1f
            val block = WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            attributes.flags = if (locked) attributes.flags or block else
                (attributes.flags and block.inv()) or (originalFlags and block)
            it.attributes = attributes
        }
        view.importantForAccessibility = if (locked) View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS else originalAccessibility
        if (locked) view.clearFocus()
    }
    DisposableEffect(window) {
        onDispose {
            view.importantForAccessibility = originalAccessibility
        }
    }
    DisposableEffect(window, controller) {
        val previous = window?.callback
        val callback = previous?.let { delegate -> object : Window.Callback by delegate {
            private fun allowInput(): Boolean {
                controller?.onInteraction()
                return controller?.locked != true && controller?.settingsUnavailable != true
            }
            override fun dispatchTouchEvent(event: MotionEvent): Boolean =
                if (allowInput()) delegate.dispatchTouchEvent(event) else true
            override fun dispatchKeyEvent(event: KeyEvent): Boolean =
                if (allowInput()) delegate.dispatchKeyEvent(event) else true
        } }
        if (callback != null) window?.callback = callback
        onDispose { if (window?.callback === callback) window?.callback = previous }
    }
}

@Composable
fun AppLockSettings() {
    val controller = LocalAppLock.current ?: return
    val activity = LocalContext.current.mainActivity() ?: return
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Privacy & app lock", style = MaterialTheme.typography.titleMedium)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("Require local authentication")
                    Text("Fingerprint or your Android screen lock", style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = controller.preferences.enabled, enabled = !controller.busy,
                    onCheckedChange = { activity.authenticateAppLock(if (it) AppLockAction.ENABLE else AppLockAction.DISABLE) })
            }
            if (!activity.appLockAuthenticationAvailable()) {
                Text("Set a secure PIN, pattern or password in Android Settings before enabling app lock.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            if (controller.preferences.enabled) {
                Choice("Lock after inactivity", controller.preferences.timeoutMillis.toString(),
                    AppLockTimeout.entries.map { it.millis.toString() to it.label }) { value ->
                    AppLockTimeout.entries.firstOrNull { it.millis.toString() == value }?.let(controller::setTimeout)
                }
                Text("The app also locks whenever it leaves the foreground. Returning from a camera or file picker requires unlocking again.",
                    style = MaterialTheme.typography.bodySmall)
            }
            controller.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Text("Screenshots and recent-app previews are protected. App lock is optional; private data encryption stays enabled independently.",
                style = MaterialTheme.typography.bodySmall)
        }
    }
}

private tailrec fun Context.mainActivity(): MainActivity? = when (this) {
    is MainActivity -> this
    is ContextWrapper -> baseContext.mainActivity()
    else -> null
}
