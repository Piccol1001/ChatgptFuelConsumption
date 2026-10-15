package my.fuelledger.security

import org.junit.Assert.*
import org.junit.Test

class AppLockSessionTest {
    @Test fun enabledSessionStartsLockedAndDoesNotUnlockByTouching() {
        var now = 1_000L
        val session = AppLockSession { now }
        session.configure(AppLockPreferences(enabled = true))
        assertTrue(session.locked)
        session.interaction()
        assertTrue(session.locked)
        session.authenticated()
        assertFalse(session.locked)
    }

    @Test fun activityResetsTimerButExpiredInputCannotReviveSession() {
        var now = 0L
        val session = AppLockSession { now }
        session.configure(AppLockPreferences(true, 30_000L)); session.authenticated()
        now = 29_000L; session.interaction()
        now = 58_999L; session.checkTimeout(); assertFalse(session.locked)
        now = 59_000L; session.interaction(); assertTrue(session.locked)
    }

    @Test fun backgroundAlwaysLocksImmediatelyIncludingDuringSystemPrompt() {
        val session = AppLockSession { 0L }
        session.configure(AppLockPreferences(true, 900_000L)); session.authenticated()
        session.background(); assertTrue(session.locked)
        // A successful system credential result can then unlock; foregrounding alone cannot.
        session.checkTimeout(); assertTrue(session.locked)
        session.authenticated(); assertFalse(session.locked)
    }

    @Test fun disabledSessionDoesNotLockInBackgroundOrAtTimeout() {
        var now = 0L
        val session = AppLockSession { now }
        session.configure(AppLockPreferences())
        now = 1_000_000L; session.background(); session.checkTimeout()
        assertFalse(session.locked)
    }

    @Test fun changingTimeoutEnforcesAlreadyElapsedInactivity() {
        var now = 0L
        val session = AppLockSession { now }
        session.configure(AppLockPreferences(true, 300_000L)); session.authenticated()
        now = 40_000L
        session.configure(AppLockPreferences(true, 30_000L))
        assertTrue(session.locked)
    }

    @Test fun deviceCredentialIsAvailableWithoutFingerprintOnAndroidEight() {
        assertEquals(LocalAuthenticationPlan.DEVICE_CREDENTIAL, localAuthenticationPlan(26, false, true, false))
        assertEquals(LocalAuthenticationPlan.UNAVAILABLE, localAuthenticationPlan(26, false, false, false))
        assertEquals(LocalAuthenticationPlan.BIOMETRIC, localAuthenticationPlan(29, true, true, false))
        assertEquals(LocalAuthenticationPlan.COMBINED, localAuthenticationPlan(30, true, true, true))
        assertEquals(LocalAuthenticationPlan.DEVICE_CREDENTIAL, localAuthenticationPlan(35, false, true, false))
    }

    @Test(expected = IllegalArgumentException::class)
    fun unsupportedTimeoutIsRejected() { AppLockPreferences(true, Long.MAX_VALUE) }
}
