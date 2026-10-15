package my.fuelledger.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.*
import java.time.temporal.TemporalAdjusters
import java.util.Locale

const val MAX_CENTS = 100_000_000_000L
val Malaysia: Locale = Locale.forLanguageTag("en-MY")

/** Money is always integer sen. Reject fractional sen instead of silently rounding input. */
object Money {
    fun parse(text: String): Long {
        require(Regex("\\d{1,10}(\\.\\d{1,2})?").matches(text.trim())) {
            "Enter an amount with at most two decimal places."
        }
        val cents = text.trim().toBigDecimal().movePointRight(2).longValueExact()
        require(cents in 1..MAX_CENTS) { "Amount must be positive and at most RM1,000,000,000." }
        return cents
    }
    fun fromEstimate(value: BigDecimal): Long = value.setScale(2, RoundingMode.HALF_UP)
        .movePointRight(2).longValueExact()
    fun decimal(cents: Long): String = BigDecimal.valueOf(cents, 2).toPlainString()
    fun format(cents: Long): String = "RM " + String.format(Malaysia, "%,.2f", BigDecimal.valueOf(cents, 2))
}

fun measurement(text: String, label: String, allowZero: Boolean = false): Double {
    val number = text.trim().toDoubleOrNull()
    require(number != null && number.isFinite() && number <= 1_000_000_000 &&
        if (allowZero) number >= 0 else number > 0) { "$label must be a finite ${if (allowZero) "non-negative" else "positive"} number." }
    return number
}

data class TripEstimate(val litres: Double, val costCents: Long, val costPerKm: Double, val kmPerLitre: Double)
fun estimateTrip(distance: Double, consumption: Double, price: BigDecimal): TripEstimate {
    require(distance.isFinite() && distance > 0 && distance <= 1e9) { "Enter a positive distance." }
    require(consumption.isFinite() && consumption > 0 && consumption <= 1e9) { "Enter a positive consumption." }
    require(price > BigDecimal.ZERO && price <= BigDecimal("1000000")) { "Enter a positive petrol price." }
    val litres = BigDecimal.valueOf(distance).multiply(BigDecimal.valueOf(consumption)).divide(BigDecimal(100))
    val cost = litres.multiply(price)
    require(cost <= BigDecimal.valueOf(MAX_CENTS, 2)) { "Trip estimate is too large." }
    return TripEstimate(litres.toDouble(), Money.fromEstimate(cost),
        cost.divide(BigDecimal.valueOf(distance), 12, RoundingMode.HALF_UP).toDouble(), 100 / consumption)
}

data class FuelPoint(val id: String, val timestamp: Long, val odometer: Double, val litres: Double,
    val cents: Long, val full: Boolean)
data class Efficiency(val startId: String, val endId: String, val distance: Double, val litres: Double,
    val cents: Long) {
    val kmPerLitre get() = distance / litres
    val litresPer100Km get() = litres / distance * 100
    val costPerKm get() = cents / 100.0 / distance
}

fun validateSequence(points: List<FuelPoint>) {
    points.forEach {
        require(it.timestamp >= 0 && it.odometer.isFinite() && it.odometer >= 0 && it.odometer <= 1e9 &&
            it.litres.isFinite() && it.litres > 0 && it.litres <= 1e9 && it.cents in 1..MAX_CENTS) { "Invalid refuelling values." }
    }
    points.sortedBy { it.timestamp }.zipWithNext().forEach { (a, b) ->
        require(b.timestamp > a.timestamp && b.odometer > a.odometer) {
            "Each refuel must have a later time and higher odometer than the preceding refuel."
        }
    }
}

/** A full tank starts a cycle. Subsequent partial fills belong to that cycle only. */
fun efficiencies(points: List<FuelPoint>): List<Efficiency> {
    val result = mutableListOf<Efficiency>()
    var baseline: FuelPoint? = null
    var litres = 0.0
    var cents = 0L
    var valid = true
    var previous: FuelPoint? = null
    for (point in points.sortedBy { it.timestamp }) {
        val numericValid = point.timestamp >= 0 && point.odometer.isFinite() && point.odometer >= 0 && point.odometer <= 1e9 &&
            point.litres.isFinite() && point.litres > 0 && point.litres <= 1e9 && point.cents in 1..MAX_CENTS
        if (!numericValid ||
            (previous != null && (point.odometer <= previous.odometer || point.timestamp <= previous.timestamp))) valid = false
        if (baseline != null) {
            litres += point.litres
            cents = try { Math.addExact(cents, point.cents) } catch (_: ArithmeticException) { valid = false; 0 }
        }
        if (point.full) {
            val start = baseline
            if (start != null && valid && litres.isFinite() && litres > 0 && point.odometer > start.odometer)
                result += Efficiency(start.id, point.id, point.odometer - start.odometer, litres, cents)
            baseline = point
            litres = 0.0
            cents = 0
            valid = numericValid
        }
        previous = point
    }
    return result
}

data class SpendingPoint(val timestamp: Long, val cents: Long, val categoryId: String)
class Spending(private val points: List<SpendingPoint>, val zone: ZoneId = ZoneId.systemDefault()) {
    fun date(timestamp: Long): LocalDate = Instant.ofEpochMilli(timestamp).atZone(zone).toLocalDate()
    fun inRange(start: LocalDate, end: LocalDate): List<SpendingPoint> {
        require(!end.isBefore(start)) { "End date must be on or after start date." }
        return points.filter { date(it.timestamp) in start..end }
    }
    fun total(start: LocalDate, end: LocalDate) = inRange(start, end).sumOf { it.cents }
    fun month(month: YearMonth) = total(month.atDay(1), month.atEndOfMonth())
    fun today(day: LocalDate) = total(day, day)
    fun week(day: LocalDate): Long {
        val monday = day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        return total(monday, monday.plusDays(6))
    }
    fun year(day: LocalDate) = total(LocalDate.of(day.year, 1, 1), LocalDate.of(day.year, 12, 31))
    fun categories(start: LocalDate, end: LocalDate) = inRange(start, end).groupBy { it.categoryId }
        .mapValues { (_, values) -> values.sumOf { it.cents } }
    fun average(start: LocalDate, end: LocalDate): Double =
        total(start, end).toDouble() / (java.time.temporal.ChronoUnit.DAYS.between(start, end) + 1) / 100
}
