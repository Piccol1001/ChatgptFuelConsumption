package my.fuelledger.data

import android.content.Context
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataMigration
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import androidx.datastore.dataStoreFile
import androidx.datastore.preferences.core.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import my.fuelledger.security.LocalVault
import java.io.File
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.io.IOException
import java.math.BigDecimal

@Serializable
data class AppSettings(val petrolPrice: String = "2.05", val consumption: Double = 7.0,
    val theme: String = "Dark") {
    fun validate() {
        val price = petrolPrice.toBigDecimalOrNull()
        require(price != null && price > BigDecimal.ZERO && price <= BigDecimal("1000000") && price.scale() <= 4) {
            "Petrol price must be positive, with at most four decimal places."
        }
        require(consumption.isFinite() && consumption > 0 && consumption <= 1000) { "Consumption must be between 0 and 1000 L/100 km." }
        require(theme in listOf("System", "Light", "Dark")) { "Invalid theme." }
    }
}
interface PreferencesStore {
    val settings: Flow<AppSettings>
    suspend fun save(settings: AppSettings)
}
class SettingsStore(context: Context, vault: LocalVault = LocalVault(context),
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)) : PreferencesStore {
    private val store = DataStoreFactory.create(serializer = EncryptedSettingsSerializer(vault),
        migrations = listOf(LegacySettingsMigration(context.applicationContext)), scope = scope,
        produceFile = { File(context.noBackupFilesDir, "settings/settings.v1.enc") })
    override val settings = store.data
    override suspend fun save(settings: AppSettings) {
        settings.validate()
        store.updateData { settings }
    }
}

private val settingsJson = Json { encodeDefaults = true; ignoreUnknownKeys = false }
private const val SETTINGS_PURPOSE = "settings:v1"

internal class EncryptedSettingsSerializer(private val vault: LocalVault) : Serializer<AppSettings> {
    override val defaultValue = AppSettings()
    override suspend fun readFrom(input: InputStream): AppSettings {
        val encrypted = input.readBytesWithLimit(65_536)
        try {
            return settingsJson.decodeFromString<AppSettings>(vault.decrypt(encrypted, SETTINGS_PURPOSE)
                .toString(Charsets.UTF_8)).also { it.validate() }
        } catch (error: Exception) {
            throw CorruptionException("Encrypted settings could not be read. Existing data was preserved.", error)
        }
    }
    override suspend fun writeTo(t: AppSettings, output: OutputStream) {
        t.validate()
        val plaintext = settingsJson.encodeToString(t).toByteArray(Charsets.UTF_8)
        try { output.write(vault.encrypt(plaintext, SETTINGS_PURPOSE)) }
        finally { plaintext.fill(0) }
    }
}

/** DataStore calls cleanup only after the migrated, encrypted settings are committed. */
private class LegacySettingsMigration(private val context: Context) : DataMigration<AppSettings> {
    private val legacyFile = context.dataStoreFile("ledger_preferences.preferences_pb")
    private var legacyScope: CoroutineScope? = null
    override suspend fun shouldMigrate(currentData: AppSettings) = legacyFile.isFile
    override suspend fun migrate(currentData: AppSettings): AppSettings {
        val readScope = CoroutineScope(SupervisorJob() + Dispatchers.IO).also { legacyScope = it }
        try {
            val old = PreferenceDataStoreFactory.create(scope = readScope, produceFile = { legacyFile }).data.first()
            return AppSettings(old[stringPreferencesKey("petrol_price")] ?: "2.05",
                old[doublePreferencesKey("consumption")] ?: 7.0,
                old[stringPreferencesKey("theme")] ?: "Dark").also { it.validate() }
        } finally {
            // This reader is no longer needed once its snapshot is taken. In particular,
            // a later encryption/write failure must not leave an active legacy DataStore.
            readScope.coroutineContext[Job]?.cancelAndJoin()
        }
    }
    override suspend fun cleanUp() {
        legacyScope?.coroutineContext?.get(Job)?.cancelAndJoin()
        if (legacyFile.exists() && !legacyFile.delete()) throw IOException("Old settings could not be removed after encryption.")
    }
}

private fun InputStream.readBytesWithLimit(limit: Int): ByteArray {
    val result = ByteArrayOutputStream()
    val chunk = ByteArray(4096)
    while (true) {
        val count = read(chunk)
        if (count == -1) break
        if (result.size() + count > limit) throw IOException("Settings file is too large.")
        result.write(chunk, 0, count)
    }
    return result.toByteArray()
}
