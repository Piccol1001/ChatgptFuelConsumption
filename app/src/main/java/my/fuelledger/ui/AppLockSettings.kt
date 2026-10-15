package my.fuelledger.ui

import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import my.fuelledger.MainActivity
import my.fuelledger.security.AppLockAction
import my.fuelledger.security.AppLockController
import my.fuelledger.security.AppLockTimeout

private val LocalAppLock = staticCompositionLocalOf<AppLockController?> { null }

/** The ledger composition is absent while locked, including editors and receipt previews. */
@Composable
fun AppLockGate(content: @Composable () -> Unit) {
    val activity = LocalContext.current.mainActivity()
    val controller = activity?.appLock
    if (controller == null) { content(); return }
    CompositionLocalProvider(LocalAppLock provides controller) {
        if (!controller.locked && !controller.settingsUnavailable) content()
        else MaterialTheme(colorScheme = LedgerDarkColors, typography = LedgerTypography, shapes = LedgerShapes) {
            Surface(Modifier.fillMaxSize()) {
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
