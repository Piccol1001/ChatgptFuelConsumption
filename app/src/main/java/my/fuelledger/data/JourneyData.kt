package my.fuelledger.data

import androidx.room.*
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import my.fuelledger.domain.*
import java.math.BigDecimal
import java.math.RoundingMode

@Serializable
data class JourneyFee(val name: String, val cents: Long, val notes: String = "")
@Serializable
data class JourneyPlan(val origin: String, val destination: String, val outboundKm: Double,
    val roundTrip: Boolean = false, val returnKm: Double = outboundKm, val consumption: Double = 7.0,
    val petrolPrice: String = "2.05", val outboundTolls: List<JourneyFee> = emptyList(),
    val returnTolls: List<JourneyFee> = emptyList(), val parkingCents: Long = 0,
    val otherFees: List<JourneyFee> = emptyList(), val people: Int = 1,
    val shareFuel: Boolean = true, val shareTolls: Boolean = true, val shareParking: Boolean = true,
    val shareOther: Boolean = true,
    // Absent in old JSON: preserve historical totals. New/refined entries explicitly use 2.
    val costVersion: Int = 1,
    // Missing in legacy records: keep their explicit saved consumption when editing.
    val consumptionSource: String = "Legacy", val routeName: String = "",
    val actualLinks: List<JourneyActualLink> = emptyList())
@Serializable data class JourneyActualLink(val expenseId: String, val kind: String)
data class JourneyActual(val fuel: Long?, val tolls: Long?, val parking: Long?)
fun journeyActual(plan: JourneyPlan, expenses: List<Expense>): JourneyActual {
    fun total(kind: String): Long? {
        val links = plan.actualLinks.filter { it.kind == kind }
        if (links.isEmpty() || links.any { link -> expenses.none { it.id == link.expenseId } }) return null
        val ids = links.map { it.expenseId }.toSet()
        return expenses.filter { it.id in ids || it.kind == "Refund" && it.refundOf in ids }.sumOf { it.cents }
    }
    return JourneyActual(total("Fuel"), total("Tolls"), total("Parking"))
}
fun reverseJourney(plan: JourneyPlan) = plan.copy(origin = plan.destination, destination = plan.origin,
    outboundKm = if (plan.roundTrip) plan.returnKm else plan.outboundKm,
    returnKm = if (plan.roundTrip) plan.outboundKm else plan.returnKm,
    outboundTolls = plan.returnTolls, returnTolls = plan.outboundTolls)
@Serializable
@Entity(tableName = "journey_plans")
data class SavedJourney(@PrimaryKey val id: String, val timestamp: Long, val payload: String) {
    fun plan(): JourneyPlan = backupJson.decodeFromString(payload)
}
data class JourneyCost(val distanceKm: Double, val fuelCents: Long, val tollCents: Long,
    val parkingCents: Long, val otherCents: Long, val sharedCents: Long, val people: Int) {
    val totalCents get() = fuelCents + tollCents + parkingCents + otherCents
    val perPersonCents get() = BigDecimal(sharedCents).divide(BigDecimal(people), 0, RoundingMode.HALF_UP).longValueExact()
}
fun journeyCost(plan: JourneyPlan): JourneyCost {
    require(plan.routeName.length <= 100 && plan.actualLinks.size <= 1000 && plan.actualLinks.map { it.expenseId }.distinct().size == plan.actualLinks.size && plan.actualLinks.all { it.expenseId.isNotBlank() && it.expenseId.length <= 100 && it.kind in setOf("Fuel", "Tolls", "Parking") }) { "Invalid route name or actual expense links." }
    require(plan.costVersion in 1..2) { "Unsupported journey calculation version." }
    require(plan.consumptionSource in setOf("Legacy", "History", "Fallback", "Override")) { "Unsupported consumption source." }
    require(plan.outboundKm.isFinite() && plan.outboundKm > 0 && plan.outboundKm <= 1e9 && plan.returnKm.isFinite() && plan.returnKm >= 0 && plan.returnKm <= 1e9) { "Enter valid outbound and return distances." }
    require(plan.people in 1..1000) { "Number of people must be 1–1,000." }
    require(plan.parkingCents in 0..MAX_CENTS) { "Invalid parking amount." }
    require(plan.outboundTolls.size <= 100 && plan.returnTolls.size <= 100 && plan.otherFees.size <= 100) { "Use at most 100 entries per fee section." }
    (plan.outboundTolls + plan.returnTolls + plan.otherFees).forEach {
        require(it.name.isNotBlank() && it.name.length <= 100 && it.notes.length <= 1000 && it.cents in 0..MAX_CENTS) { "Check fee name, amount and notes." }
    }
    val distance = plan.outboundKm + if (plan.roundTrip) plan.returnKm else 0.0
    val fuel = estimateTrip(distance, plan.consumption, plan.petrolPrice.toBigDecimal()).costCents
    val tolls = plan.outboundTolls.sumOf { it.cents } + if (plan.roundTrip) plan.returnTolls.sumOf { it.cents } else 0
    val other = if (plan.costVersion == 1) plan.otherFees.sumOf { it.cents } else 0L
    require(fuel + tolls + plan.parkingCents + other <= MAX_CENTS) { "Journey cost exceeds the supported amount." }
    val shared = if (plan.costVersion == 2) 0L else (if (plan.shareFuel) fuel else 0) + (if (plan.shareTolls) tolls else 0) +
        (if (plan.shareParking) plan.parkingCents else 0) + (if (plan.shareOther) other else 0)
    return JourneyCost(distance, fuel, tolls, plan.parkingCents, other, shared, plan.people)
}
fun validateSavedJourney(value: SavedJourney) {
    require(value.id.isNotBlank() && value.id.length <= 64 && value.timestamp in 0..32_503_680_000_000L) { "Invalid journey record." }
    val plan = value.plan()
    require(plan.origin.isNotBlank() && plan.destination.isNotBlank() && plan.origin.length <= 200 && plan.destination.length <= 200) { "Enter origin and destination names (up to 200 characters)." }
    journeyCost(plan)
}
fun savedJourney(id: String, plan: JourneyPlan, timestamp: Long = System.currentTimeMillis()) =
    SavedJourney(id, timestamp, backupJson.encodeToString(plan)).also(::validateSavedJourney)
@Dao
interface JourneyDao {
    @Query("SELECT * FROM journey_plans ORDER BY timestamp DESC, id") fun journeys(): Flow<List<SavedJourney>>
    @Query("SELECT * FROM journey_plans ORDER BY id") suspend fun allJourneys(): List<SavedJourney>
    @Upsert suspend fun put(value: SavedJourney)
    @Query("DELETE FROM journey_plans WHERE id = :id") suspend fun delete(id: String)
    @Query("DELETE FROM journey_plans") suspend fun clear()
}
class JourneyRepository(private val database: LedgerDatabase) {
    val journeys = database.journeyDao().journeys()
    suspend fun save(value: SavedJourney) {
        validateSavedJourney(value)
        database.withTransaction {
            val links = value.plan().actualLinks.map { it.expenseId }.toSet()
            val others = database.journeyDao().allJourneys().filter { it.id != value.id }.flatMap { it.plan().actualLinks }.map { it.expenseId }.toSet()
            require(links.intersect(others).isEmpty()) { "An expense is already attributed to another journey." }
            val previous = database.journeyDao().allJourneys().find { it.id == value.id }?.plan()?.actualLinks?.map { it.expenseId }.orEmpty().toSet()
            require(database.dao().allExpenses().map { it.id }.toSet().containsAll(links - previous)) { "A selected expense no longer exists." }
            database.journeyDao().put(value)
        }
    }
    suspend fun delete(id: String) = database.journeyDao().delete(id)
}
