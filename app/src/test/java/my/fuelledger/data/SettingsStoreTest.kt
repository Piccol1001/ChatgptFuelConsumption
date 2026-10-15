package my.fuelledger.data

import android.content.Context
import android.content.ContextWrapper
import androidx.datastore.core.CorruptionException
import androidx.datastore.dataStoreFile
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import my.fuelledger.security.LocalVault
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.security.GeneralSecurityException
import java.util.UUID
import javax.crypto.spec.SecretKeySpec

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 35], application = android.app.Application::class)
class SettingsStoreTest {
    private lateinit var context: Context
    private lateinit var directory: File
    private val scopes = mutableListOf<CoroutineScope>()
    private val vault = LocalVault { SecretKeySpec(ByteArray(32) { (it + 1).toByte() }, "AES") }
    private fun scope() = CoroutineScope(SupervisorJob() + Dispatchers.IO).also { scopes += it }
    private val encryptedFile get() = File(context.noBackupFilesDir, "settings/settings.v1.enc")
    private val legacyFile get() = context.dataStoreFile("ledger_preferences.preferences_pb")

    @Before fun setup() {
        val app = ApplicationProvider.getApplicationContext<Context>()
        directory = File(app.cacheDir, "settings-test-${UUID.randomUUID()}").apply { mkdirs() }
        context = object : ContextWrapper(app) {
            override fun getApplicationContext(): Context = this
            override fun getFilesDir() = File(directory, "files").apply { mkdirs() }
            override fun getNoBackupFilesDir() = File(directory, "no-backup").apply { mkdirs() }
        }
    }
    @After fun cleanup() = runBlocking {
        scopes.forEach { it.coroutineContext[Job]?.cancelAndJoin() }
        directory.deleteRecursively()
        Unit
    }
    private suspend fun legacy(settings: AppSettings) {
        val oldScope = scope()
        val store = PreferenceDataStoreFactory.create(scope = oldScope, produceFile = { legacyFile })
        store.edit {
            it[stringPreferencesKey("petrol_price")] = settings.petrolPrice
            it[doublePreferencesKey("consumption")] = settings.consumption
            it[stringPreferencesKey("theme")] = settings.theme
        }
        oldScope.coroutineContext[Job]?.cancelAndJoin()
    }

    @Test fun oldSettingsMigrateWithoutLossAndRemainEncryptedAfterStoreRestart() = runBlocking {
        val original = AppSettings("3.1234", 8.75, "Light")
        legacy(original)
        val firstScope = scope()
        val migrated = SettingsStore(context, vault, firstScope)
        assertEquals(original, migrated.settings.first())
        assertFalse(legacyFile.exists())
        assertTrue(encryptedFile.isFile)
        val encrypted = encryptedFile.readBytes()
        assertFalse(encrypted.toString(Charsets.ISO_8859_1).contains("3.1234"))
        assertTrue(vault.decrypt(encrypted, "settings:v1").toString(Charsets.UTF_8).contains("3.1234"))
        val updated = AppSettings("2.55", 6.25, "Dark")
        migrated.save(updated)
        firstScope.coroutineContext[Job]?.cancelAndJoin()
        assertEquals(updated, SettingsStore(context, vault, scope()).settings.first())
    }

    @Test fun failedEncryptionPreservesLegacySettingsAndSuccessfulRetryMigratesThem() = runBlocking {
        val original = AppSettings("4.10", 9.5, "System")
        legacy(original)
        val originalBytes = legacyFile.readBytes()
        val failingScope = scope()
        val failing = SettingsStore(context, LocalVault { throw GeneralSecurityException("Test key unavailable") }, failingScope)
        try { failing.settings.first(); fail("Expected encryption failure") } catch (_: GeneralSecurityException) {}
        failingScope.coroutineContext[Job]?.cancelAndJoin()
        assertArrayEquals(originalBytes, legacyFile.readBytes())
        assertFalse(encryptedFile.exists())
        assertEquals(original, SettingsStore(context, vault, scope()).settings.first())
        assertFalse(legacyFile.exists())
    }

    @Test fun tamperedOrWrongKeySettingsNeverResetToDefaultsOrOverwriteData() = runBlocking {
        val initialScope = scope()
        SettingsStore(context, vault, initialScope).save(AppSettings("5.25", 10.5, "Light"))
        initialScope.coroutineContext[Job]?.cancelAndJoin()
        val intact = encryptedFile.readBytes()
        val wrongScope = scope()
        val wrong = SettingsStore(context, LocalVault { SecretKeySpec(ByteArray(32) { 42 }, "AES") }, wrongScope)
        try { wrong.settings.first(); fail("Expected wrong-key failure") } catch (_: CorruptionException) {}
        wrongScope.coroutineContext[Job]?.cancelAndJoin()
        assertArrayEquals(intact, encryptedFile.readBytes())
        val tampered = intact.copyOf().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }
        encryptedFile.writeBytes(tampered)
        val badScope = scope()
        try { SettingsStore(context, vault, badScope).settings.first(); fail("Expected authentication failure") }
        catch (_: CorruptionException) {}
        badScope.coroutineContext[Job]?.cancelAndJoin()
        assertArrayEquals(tampered, encryptedFile.readBytes())
    }

    @Test fun newInstallDefaultsToSystemAndAllThemeScannerPreferencesSurviveRestart() = runBlocking {
        var active = scope()
        var store = SettingsStore(context, vault, active)
        assertEquals("System", store.settings.first().theme)
        assertEquals("Review", store.settings.first().receiptSaveMode)
        for (theme in listOf("Dark", "Light", "System")) {
            val preferences = AppSettings("2.95", 6.75, theme, true, "Smart", false)
            store.save(preferences)
            active.coroutineContext[Job]?.cancelAndJoin()
            active = scope(); store = SettingsStore(context, vault, active)
            assertEquals(preferences, store.settings.first())
        }
    }
    @Test fun oldEncryptedSettingsWithoutNewFieldsPreserveSelectedTheme() = runBlocking {
        encryptedFile.parentFile!!.mkdirs()
        encryptedFile.writeBytes(vault.encrypt("""{"petrolPrice":"2.05","consumption":7.0,"theme":"Dark"}""".toByteArray(), "settings:v1"))
        val settings = SettingsStore(context, vault, scope()).settings.first()
        assertEquals("Dark", settings.theme); assertEquals("Review", settings.receiptSaveMode)
        assertFalse(settings.plannerReminders)
    }
    @Test fun invalidLegacyValuesRemainAvailableForRecovery() = runBlocking {
        legacy(AppSettings("2.05", 7.0, "Unsupported theme"))
        val before = legacyFile.readBytes()
        try { SettingsStore(context, vault, scope()).settings.first(); fail("Expected invalid legacy settings") }
        catch (_: IllegalArgumentException) {}
        assertArrayEquals(before, legacyFile.readBytes())
        assertFalse(encryptedFile.exists())
    }
}
