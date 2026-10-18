package my.fuelledger

import android.os.Process
import android.provider.Settings
import android.view.WindowManager
import androidx.lifecycle.ViewModelProvider
import android.content.Intent
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleCallback
import androidx.test.runner.lifecycle.Stage
import java.util.concurrent.atomic.AtomicReference
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Launches the real application, including SQLCipher, DataStore and recurring-work setup. */
@RunWith(AndroidJUnit4::class)
class ProductionStartupTest {
    @Test fun freshOfflineActivityReachesLedgerAndSurvivesRecreation() = runBlocking {
        // This acceptance check must never modify or inspect an owner's ledger.
        check(Process.myUid() / 100_000 != 0) { "Run startup acceptance in an isolated Android test user." }
        val app = ApplicationProvider.getApplicationContext<LedgerApplication>()
        assertEquals(1, Settings.Global.getInt(app.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0))
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val active = AtomicReference<MainActivity?>()
        val monitor = ActivityLifecycleMonitorRegistry.getInstance()
        val callback = ActivityLifecycleCallback { activity, stage ->
            if (activity is MainActivity && stage == Stage.RESUMED) active.set(activity)
        }
        monitor.addLifecycleCallback(callback)
        try {
            // startActivitySync waits for an idle queue, which an animated loading screen
            // need not provide on slow software emulators. Assert lifecycle and readiness
            // directly instead; neither loading text nor a successful launch is a pass.
            app.startActivity(Intent(app, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            val activity = withTimeout(60_000) { while (active.get() == null) delay(50); active.get()!! }
            lateinit var model: LedgerViewModel
            instrumentation.runOnMainSync {
                model = ViewModelProvider(activity)[LedgerViewModel::class.java]
                assertEquals(0, activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE)
            }
            withTimeout(120_000) { model.ready.first { it } }
            val initial = withTimeout(30_000) { model.state.first { it.categories.isNotEmpty() } }
            assertTrue(initial.categories.map { it.id }.containsAll(my.fuelledger.data.defaultCategories.map { it.id }))
            instrumentation.runOnMainSync { activity.recreate() }
            val recreated = withTimeout(60_000) { while (active.get() === activity) delay(50); active.get()!! }
            instrumentation.runOnMainSync { assertSame(model, ViewModelProvider(recreated)[LedgerViewModel::class.java]) }
            assertTrue(model.ready.value)
        } finally {
            monitor.removeLifecycleCallback(callback)
            active.get()?.let { activity -> instrumentation.runOnMainSync { activity.finish() } }
        }
    }
}
