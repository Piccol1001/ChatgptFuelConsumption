package my.fuelledger

import android.os.Bundle
import android.view.WindowManager
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.Window
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import my.fuelledger.security.AppLockAction
import my.fuelledger.security.AppLockAuthenticator
import my.fuelledger.security.AppLockController
import my.fuelledger.ui.AppLockGate
import my.fuelledger.ui.LedgerApp

class MainActivity : FragmentActivity() {
    lateinit var appLock: AppLockController; private set
    private lateinit var authentication: AppLockAuthenticator

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        appLock = ViewModelProvider(this)[AppLockController::class.java]
        authentication = AppLockAuthenticator(this, appLock)
        val delegate = window.callback
        window.callback = object : Window.Callback by delegate {
            override fun dispatchTouchEvent(event: MotionEvent): Boolean =
                if (lockExpiredOnInput()) true else delegate.dispatchTouchEvent(event)
            override fun dispatchKeyEvent(event: KeyEvent): Boolean =
                if (lockExpiredOnInput()) true else delegate.dispatchKeyEvent(event)
        }
        enableEdgeToEdge()
        setContent { AppLockGate { LedgerApp(viewModel()) } }
    }

    override fun onStart() { super.onStart(); appLock.onForeground() }
    override fun onStop() { appLock.onBackground(); super.onStop() }
    override fun onUserInteraction() { super.onUserInteraction(); if (::appLock.isInitialized) appLock.onInteraction() }
    private fun lockExpiredOnInput(): Boolean {
        if (!::appLock.isInitialized) return false
        val wasLocked = appLock.locked
        appLock.onInteraction()
        // Do not let the event that discovers expiry activate an underlying ledger control.
        return !wasLocked && appLock.locked
    }
    fun authenticateAppLock(action: AppLockAction) = authentication.authenticate(action)
    fun appLockAuthenticationAvailable() = authentication.available()
}
