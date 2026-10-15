package my.fuelledger.security

import android.app.Application
import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.util.AtomicFile
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer

/** Only settings are stored here. The lock never stores credentials or replaces data encryption. */
data class AppLockPreferences(val enabled: Boolean = false, val timeoutMillis: Long = 60_000L) {
    init { require(timeoutMillis in AppLockTimeout.entries.map { it.millis }) }
}

enum class AppLockTimeout(val millis: Long, val label: String) {
    THIRTY_SECONDS(30_000L, "30 seconds"), ONE_MINUTE(60_000L, "1 minute"),
    FIVE_MINUTES(300_000L, "5 minutes"), FIFTEEN_MINUTES(900_000L, "15 minutes")
}

/** Monotonic time is unaffected by changes to the wall clock or time zone. */
class AppLockSession(private val clock: () -> Long = SystemClock::elapsedRealtime) {
    var enabled: Boolean = false; private set
    var locked: Boolean = false; private set
    var timeoutMillis: Long = 60_000L; private set
    private var lastInteraction: Long = clock()

    fun configure(preferences: AppLockPreferences) {
        if (preferences.enabled && !enabled) locked = true
        if (!preferences.enabled) locked = false
        enabled = preferences.enabled
        timeoutMillis = preferences.timeoutMillis
        checkTimeout()
    }

    fun authenticated() { locked = false; lastInteraction = clock() }
    fun background() { if (enabled) locked = true }
    fun interaction() {
        // An input arriving after expiry must not revive an expired session.
        checkTimeout()
        if (!locked) lastInteraction = clock()
    }
    fun checkTimeout() {
        if (enabled && !locked && clock() - lastInteraction >= timeoutMillis) locked = true
    }
    fun remainingMillis(): Long = (timeoutMillis - (clock() - lastInteraction)).coerceAtLeast(0L)
}

enum class LocalAuthenticationPlan { COMBINED, BIOMETRIC, DEVICE_CREDENTIAL, UNAVAILABLE }

/** Older Android versions cannot use the combined authenticator mask. */
fun localAuthenticationPlan(sdk: Int, biometric: Boolean, secureCredential: Boolean,
    combinedAvailable: Boolean): LocalAuthenticationPlan = when {
    sdk >= 30 && combinedAvailable -> LocalAuthenticationPlan.COMBINED
    biometric -> LocalAuthenticationPlan.BIOMETRIC
    secureCredential -> LocalAuthenticationPlan.DEVICE_CREDENTIAL
    else -> LocalAuthenticationPlan.UNAVAILABLE
}

private class AppLockStore(context: Context) {
    private val vault = LocalVault(context)
    private val path = File(context.noBackupFilesDir, "security/app-lock.bin")
    private val file = AtomicFile(path)
    fun read(): AppLockPreferences {
        if (!path.exists() && !File(path.path + ".bak").exists()) return AppLockPreferences()
        val encrypted = file.openRead().use { input ->
            val bytes = input.readNBytes(8193)
            require(bytes.size <= 8192) { "Invalid lock settings." }
            bytes
        }
        val plain = vault.decrypt(encrypted, "app-lock-settings-v1")
        try {
            require(plain.size == 13) { "Invalid lock settings." }
            val buffer = ByteBuffer.wrap(plain)
            require(buffer.int == 1) { "Unknown lock settings version." }
            val flag = buffer.get().toInt()
            require(flag == 0 || flag == 1) { "Invalid lock settings." }
            return AppLockPreferences(flag == 1, buffer.long)
        } finally { plain.fill(0) }
    }
    fun write(preferences: AppLockPreferences) {
        val plain = ByteBuffer.allocate(13).putInt(1).put(if (preferences.enabled) 1.toByte() else 0.toByte())
            .putLong(preferences.timeoutMillis).array()
        val encrypted = try { vault.encrypt(plain, "app-lock-settings-v1") } finally { plain.fill(0) }
        check(path.parentFile!!.isDirectory || path.parentFile!!.mkdirs()) { "Cannot create private settings." }
        val output = file.startWrite()
        try { output.write(encrypted); file.finishWrite(output) }
        catch (error: Exception) { file.failWrite(output); throw error }
    }
}

enum class AppLockAction { UNLOCK, ENABLE, DISABLE }

class AppLockController(application: Application) : AndroidViewModel(application) {
    private val store = AppLockStore(application)
    private val session = AppLockSession()
    private var timer: Job? = null
    private var foreground = false
    private var action: AppLockAction? = null
    var preferences by mutableStateOf(AppLockPreferences()); private set
    var locked by mutableStateOf(false); private set
    var busy by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set
    var settingsUnavailable by mutableStateOf(false); private set

    init { reload() }

    fun reload() {
        runCatching { store.read() }.onSuccess {
            preferences = it; settingsUnavailable = false; error = null
            session.configure(it); publish()
        }.onFailure {
            // An unreadable existing file must never silently turn a configured lock off.
            settingsUnavailable = true; locked = true
            error = "Private security settings could not be read. Restart your device and try again."
        }
    }
    fun onForeground() { foreground = true; session.checkTimeout(); publish(); schedule() }
    fun onBackground() { foreground = false; session.background(); publish(); timer?.cancel() }
    fun onInteraction() { session.interaction(); publish(); schedule() }

    fun beginAuthentication(request: AppLockAction): Boolean {
        if (busy || settingsUnavailable) return false
        action = request; busy = true; error = null; timer?.cancel()
        return true
    }
    fun authenticationCancelled(message: String) { action = null; busy = false; error = message; schedule() }
    fun authenticationSucceeded() {
        val request = action ?: return
        action = null
        val next = when (request) {
            AppLockAction.ENABLE -> preferences.copy(enabled = true)
            AppLockAction.DISABLE -> preferences.copy(enabled = false)
            AppLockAction.UNLOCK -> preferences
        }
        viewModelScope.launch {
            runCatching { if (request != AppLockAction.UNLOCK) withContext(Dispatchers.IO) { store.write(next) } }
                .onSuccess {
                    preferences = next; session.configure(next); session.authenticated()
                    if (!foreground) session.background()
                    error = null
                }.onFailure { error = "Security settings could not be saved. No settings were changed." }
            busy = false; publish(); schedule()
        }
    }
    fun setTimeout(timeout: AppLockTimeout) {
        if (busy || settingsUnavailable) return
        val next = preferences.copy(timeoutMillis = timeout.millis)
        busy = true; error = null
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { store.write(next) } }
                .onSuccess { preferences = next; session.configure(next) }
                .onFailure { error = "Security settings could not be saved. No settings were changed." }
            busy = false; publish(); schedule()
        }
    }
    private fun publish() { if (!settingsUnavailable) locked = session.locked }
    private fun schedule() {
        timer?.cancel()
        if (!foreground || !preferences.enabled || locked || busy || settingsUnavailable) return
        timer = viewModelScope.launch {
            delay(session.remainingMillis().coerceAtLeast(1L))
            session.checkTimeout(); publish(); schedule()
        }
    }
}

/** System prompts only. The credential fallback also works on API 26 without enrolled fingerprints. */
class AppLockAuthenticator(private val activity: FragmentActivity, private val controller: AppLockController) {
    private val keyguard = activity.getSystemService(KeyguardManager::class.java)
    private val credentialResult: ActivityResultLauncher<android.content.Intent> = activity.registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) controller.authenticationSucceeded()
        else controller.authenticationCancelled("Authentication cancelled.")
    }
    private val prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(activity),
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                controller.authenticationSucceeded()
            }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                if (errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON && keyguard?.isDeviceSecure == true) {
                    launchCredential()
                } else controller.authenticationCancelled(when (errorCode) {
                    BiometricPrompt.ERROR_CANCELED, BiometricPrompt.ERROR_USER_CANCELED,
                    BiometricPrompt.ERROR_NEGATIVE_BUTTON -> "Authentication cancelled."
                    else -> "Authentication could not finish. Try again or use your device credential."
                })
            }
        })

    fun available(): Boolean = plan() != LocalAuthenticationPlan.UNAVAILABLE

    fun authenticate(action: AppLockAction) {
        if (!controller.beginAuthentication(action)) return
        val plan = plan()
        if (plan == LocalAuthenticationPlan.UNAVAILABLE) {
            controller.authenticationCancelled("Set a secure screen lock (PIN, pattern or password) in Android Settings first.")
            return
        }
        if (plan == LocalAuthenticationPlan.DEVICE_CREDENTIAL) { launchCredential(); return }
        val builder = BiometricPrompt.PromptInfo.Builder().setTitle("Unlock Fuel & Expense Tracker")
            .setSubtitle("Authenticate on this device to protect your private financial records.")
        if (plan == LocalAuthenticationPlan.COMBINED) {
            builder.setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
        } else {
            builder.setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                .setNegativeButtonText(if (keyguard?.isDeviceSecure == true) "Use device PIN" else "Cancel")
        }
        runCatching { prompt.authenticate(builder.build()) }.onFailure {
            if (keyguard?.isDeviceSecure == true) launchCredential()
            else controller.authenticationCancelled("Authentication is unavailable on this device.")
        }
    }
    private fun plan(): LocalAuthenticationPlan {
        val manager = BiometricManager.from(activity)
        val strong = manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS
        val combined = Build.VERSION.SDK_INT >= 30 && manager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        ) == BiometricManager.BIOMETRIC_SUCCESS
        return localAuthenticationPlan(Build.VERSION.SDK_INT, strong, keyguard?.isDeviceSecure == true, combined)
    }
    @Suppress("DEPRECATION")
    private fun launchCredential() {
        val intent = keyguard?.createConfirmDeviceCredentialIntent("Unlock Fuel & Expense Tracker", "Confirm your device screen lock.")
        if (intent == null) controller.authenticationCancelled("Set a secure screen lock in Android Settings first.")
        else runCatching { credentialResult.launch(intent) }.onFailure {
            controller.authenticationCancelled("The device credential screen is unavailable.")
        }
    }
}
