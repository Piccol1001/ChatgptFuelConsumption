package my.fuelledger

import android.os.Bundle
import android.view.WindowManager
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
        enableEdgeToEdge()
        setContent { AppLockGate { LedgerApp(viewModel()) } }
    }

    override fun onStart() { super.onStart(); appLock.onForeground() }
    override fun onStop() { appLock.onBackground(); super.onStop() }
    override fun onUserInteraction() { super.onUserInteraction(); if (::appLock.isInitialized) appLock.onInteraction() }
    fun authenticateAppLock(action: AppLockAction) = authentication.authenticate(action)
    fun appLockAuthenticationAvailable() = authentication.available()
}
