package my.fuelledger.data

import java.time.*
import java.time.format.DateTimeFormatter

/** Stable across query order, process restart, category edits and backup restore. */
fun displayCategories(categories: List<Category>): List<Category> {
    val builtins = listOf("food", "groceries", "entertainment", "shopping", FUEL_CATEGORY, "bills")
    return categories.sortedWith(compareBy<Category> { if (it.id == OTHER_CATEGORY) 2 else if (it.id in builtins) 0 else 1 }
        .thenBy { builtins.indexOf(it.id).let { index -> if (index < 0) Int.MAX_VALUE else index } }
        .thenBy { it.name.lowercase(java.util.Locale.ROOT) }.thenBy { it.id })
}
fun fuelSpendingMonths(refuels: List<Refuel>, today: LocalDate, count: Int,
    zone: ZoneId = ZoneId.systemDefault()): List<Pair<String, Long>> {
    require(count in listOf(3, 6, 12))
    val current = YearMonth.from(today)
    val amounts = refuels.groupBy { YearMonth.from(Instant.ofEpochMilli(it.timestamp).atZone(zone)) }.mapValues { (_, values) -> values.sumOf { it.cents } }
    val labels = DateTimeFormatter.ofPattern("MMM yy", java.util.Locale.ENGLISH)
    return (count - 1 downTo 0).map { offset -> current.minusMonths(offset.toLong()).let { it.format(labels) to (amounts[it] ?: 0L) } }
}
val incomeSourceTypes = listOf("Salary", "Allowance", "Stock / Investment Income", "Passive Income", "Part-Time Income", "Other Income")
fun incomeSourceType(rule: FinanceRecurring): String = rule.categoryId.removePrefix("income:").takeIf { it in incomeSourceTypes } ?: "Other Income"

fun flexibleAllocationId(categories: List<AllocationCategory>): String =
    categories.firstOrNull { it.remainder }?.id ?: categories.last().id
/** Only the chosen category and the visible remainder change. */
fun moveAllocation(categories: List<AllocationCategory>, id: String, requested: Int): List<AllocationCategory> {
    require(categories.isNotEmpty() && categories.sumOf { it.basisPoints } == 10000 && categories.all { it.basisPoints in 0..10000 })
    val flexible = flexibleAllocationId(categories)
    if (id == flexible) return categories
    val selected = requireNotNull(categories.find { it.id == id })
    val remainder = categories.first { it.id == flexible }
    val target = requested.coerceIn(0, selected.basisPoints + remainder.basisPoints)
    val delta = target - selected.basisPoints
    return categories.map { when (it.id) { id -> it.copy(basisPoints = target); flexible -> it.copy(basisPoints = it.basisPoints - delta); else -> it } }
}
fun removeAllocation(categories: List<AllocationCategory>, id: String): List<AllocationCategory> {
    val flexible = flexibleAllocationId(categories)
    require(id != flexible) { "Choose another flexible category before removing this one." }
    val removed = requireNotNull(categories.find { it.id == id })
    return categories.filterNot { it.id == id }.map { if (it.id == flexible) it.copy(basisPoints = it.basisPoints + removed.basisPoints) else it }
}
