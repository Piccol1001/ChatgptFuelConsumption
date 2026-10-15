package my.fuelledger.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import java.math.BigDecimal

val Context.ledgerPreferences by preferencesDataStore(name = "ledger_preferences")
@Serializable
data class AppSettings(val petrolPrice: String = "2.05", val consumption: Double = 7.0,
    val theme: String = "System") {
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
class SettingsStore(context: Context) : PreferencesStore {
    private val store = context.ledgerPreferences
    private val price = stringPreferencesKey("petrol_price")
    private val consumption = doublePreferencesKey("consumption")
    private val theme = stringPreferencesKey("theme")
    override val settings = store.data.map { AppSettings(it[price] ?: "2.05", it[consumption] ?: 7.0, it[theme] ?: "System") }
    override suspend fun save(settings: AppSettings) {
        settings.validate()
        store.edit { it[price] = settings.petrolPrice; it[consumption] = settings.consumption; it[theme] = settings.theme }
    }
}
