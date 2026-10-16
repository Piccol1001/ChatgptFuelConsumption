package my.fuelledger.domain

import my.fuelledger.data.*

/** Two recent cycles against at least two earlier cycles, with non-overlapping baselines. */
data class ConsumptionChange(val historical: Double, val recent: Double) {
    val percent get() = (recent - historical) / historical * 100
}
fun consumptionChange(points: List<FuelPoint>): ConsumptionChange? {
    val cycles = efficiencies(points)
    if (cycles.size < 4) return null
    fun average(values: List<Efficiency>) = values.sumOf { it.litres } / values.sumOf { it.distance } * 100
    return ConsumptionChange(average(cycles.dropLast(2)), average(cycles.takeLast(2)))
}
data class VehicleCoverage(val start: Long, val end: Long, val distance: Double, val fuelCents: Long,
    val operatingCents: Long, val categoryIds: Set<String>) {
    val fuelRmPerKm get() = fuelCents / 100.0 / distance
    val recordedRmPerKm get() = operatingCents / 100.0 / distance
}
fun vehicleCoverage(refuels: List<Refuel>, expenses: List<Expense>, categories: List<Category>): VehicleCoverage? {
    val cycles = efficiencies(refuels.map { it.point() })
    if (cycles.isEmpty()) return null
    val byId = refuels.associateBy { it.id }
    val ids = categories.filter { it.id == FUEL_CATEGORY || it.name.lowercase() in setOf("parking", "tolls", "vehicle", "transport") }.map { it.id }.toSet()
    // Include manual recorded transport costs only within measured intervals. Linked refuels
    // are already represented by cycle payments; baseline purchases are never counted twice.
    val extras = expenses.filter { it.fuelId == null && it.categoryId in ids && cycles.any { c ->
        it.timestamp > byId.getValue(c.startId).timestamp && it.timestamp <= byId.getValue(c.endId).timestamp
    } }.sumOf { it.cents }
    val fuel = cycles.sumOf { it.cents }
    return VehicleCoverage(byId.getValue(cycles.first().startId).timestamp, byId.getValue(cycles.last().endId).timestamp,
        cycles.sumOf { it.distance }, fuel, fuel + extras, ids)
}
fun averageFuelPrice(refuels: List<Refuel>): Double? = refuels.takeIf { it.isNotEmpty() }?.let { values -> values.sumOf { it.cents } / 100.0 / values.sumOf { it.litres } }

data class FuelCostDrivers(val priceRmPerKm: Double, val consumptionRmPerKm: Double, val totalChangeRmPerKm: Double)
fun fuelCostDrivers(points: List<FuelPoint>): FuelCostDrivers? {
    val cycles = efficiencies(points)
    if (cycles.size < 4) return null
    val prior = cycles.dropLast(2); val recent = cycles.takeLast(2)
    val priorPrice = prior.sumOf { it.cents } / 100.0 / prior.sumOf { it.litres }
    val recentPrice = recent.sumOf { it.cents } / 100.0 / recent.sumOf { it.litres }
    val priorUse = prior.sumOf { it.litres } / prior.sumOf { it.distance }
    val recentUse = recent.sumOf { it.litres } / recent.sumOf { it.distance }
    return FuelCostDrivers((recentPrice - priorPrice) * priorUse, (recentUse - priorUse) * recentPrice,
        recentUse * recentPrice - priorUse * priorPrice)
}
