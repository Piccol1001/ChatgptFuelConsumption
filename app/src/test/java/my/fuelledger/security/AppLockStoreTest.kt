package my.fuelledger.security

import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import my.fuelledger.testing.TestLedgerApplication
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID
import javax.crypto.KeyGenerator

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 35], application = TestLedgerApplication::class)
class AppLockStoreTest {
    private lateinit var context: Context
    private lateinit var vault: LocalVault
    private lateinit var store: AppLockStore
    @Before fun setUp() {
        val app = ApplicationProvider.getApplicationContext<Context>()
        val directory = File(app.cacheDir, "lock-store-test-${UUID.randomUUID()}").apply { mkdirs() }
        context = object : ContextWrapper(app) { override fun getNoBackupFilesDir() = directory }
        val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        vault = LocalVault { key }
        store = AppLockStore(context, vault)
    }

    @Test fun newInstallHasOptionalLockDisabledWithoutCreatingPlaintextSettings() {
        assertEquals(AppLockPreferences(), store.read())
        assertFalse(File(context.noBackupFilesDir, "security/app-lock.bin").exists())
    }

    @Test fun enabledSettingSurvivesReopeningAndCiphertextCannotBeParsedAsSettings() {
        val expected = AppLockPreferences(true, AppLockTimeout.FIVE_MINUTES.millis)
        store.write(expected)
        assertEquals(expected, AppLockStore(context, vault).read())
        val ciphertext = File(context.noBackupFilesDir, "security/app-lock.bin").readBytes()
        assertTrue(ciphertext.size > 13)
        assertFalse(ciphertext.contentEquals(java.nio.ByteBuffer.allocate(13).putInt(1).put(1.toByte()).putLong(300_000L).array()))
    }

    @Test fun tamperingDoesNotFallBackToDisabledDefaults() {
        store.write(AppLockPreferences(true))
        val file = File(context.noBackupFilesDir, "security/app-lock.bin")
        val bytes = file.readBytes(); bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte(); file.writeBytes(bytes)
        assertTrue(runCatching { store.read() }.isFailure)
    }

    @Test fun oversizedPrivateFileIsRejectedBeforeDecryption() {
        File(context.noBackupFilesDir, "security").mkdirs()
        File(context.noBackupFilesDir, "security/app-lock.bin").writeBytes(ByteArray(8193))
        assertTrue(runCatching { store.read() }.isFailure)
    }

    @Test fun ciphertextFromAnotherPurposeCannotReplaceLockPreferences() {
        File(context.noBackupFilesDir, "security").mkdirs()
        File(context.noBackupFilesDir, "security/app-lock.bin").writeBytes(vault.encrypt(ByteArray(13), "receipt-photo-v1"))
        assertTrue(runCatching { store.read() }.isFailure)
    }
}
