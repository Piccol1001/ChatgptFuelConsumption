package my.fuelledger.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import my.fuelledger.domain.*

val backupJson = Json { prettyPrint = true; encodeDefaults = true; ignoreUnknownKeys = false }
@Serializable
data class Backup(val format: String, val version: Int,
    val createdAt: Long, val categories: List<Category>, val expenses: List<Expense>,
    val refuels: List<Refuel>, val settings: AppSettings)

fun validateExpense(expense: Expense) {
    require(expense.id.isNotBlank() && expense.id.length <= 100) { "Invalid expense ID." }
    require(expense.timestamp >= 0 && expense.timestamp <= 32_503_680_000_000L) { "Invalid expense date." }
    require(expense.cents in 1..MAX_CENTS) { "Amount must be positive." }
    require(expense.description.length <= 200 && expense.notes.length <= 2000) { "Description or notes are too long." }
    require(expense.payment in paymentMethods) { "Choose a valid payment method." }
}
fun validateRefuel(refuel: Refuel) {
    require(refuel.id.isNotBlank() && refuel.id.length <= 100 && refuel.timestamp in 0..32_503_680_000_000L) { "Invalid refuel ID or date." }
    require(refuel.station.length <= 100 && refuel.notes.length <= 2000) { "Station or notes are too long." }
    validateSequence(listOf(refuel.point()))
}
fun validateCategory(category: Category) {
    require(category.id.isNotBlank() && category.id.length <= 100 && category.name.isNotBlank() && category.name.length <= 50) { "Category name must contain 1–50 characters." }
    require(category.icon in categoryIcons && category.color in categoryColors) { "Choose a supported icon and colour." }
}
fun Backup.validate() {
    require(format == "my.fuelledger.backup" && version == 1) { "This is not a compatible Fuel & Expense Tracker backup." }
    require(categories.size in 7..500 && expenses.size <= 100_000 && refuels.size <= 100_000) { "Backup contains too many records or is missing default categories." }
    require(categories.map { it.id }.distinct().size == categories.size &&
        expenses.map { it.id }.distinct().size == expenses.size && refuels.map { it.id }.distinct().size == refuels.size) { "Backup contains duplicate IDs." }
    require(categories.map { it.name.lowercase(java.util.Locale.ROOT) }.distinct().size == categories.size) { "Backup contains duplicate category names." }
    categories.forEach(::validateCategory)
    defaultCategories.forEach { default -> require(categories.any { it == default }) { "Default category definitions are missing or modified." } }
    require(categories.filter { !it.custom }.map { it.id }.toSet() == defaultCategories.map { it.id }.toSet()) { "Unknown built-in category." }
    expenses.forEach(::validateExpense)
    refuels.forEach(::validateRefuel)
    validateSequence(refuels.map { it.point() })
    val categoryIds = categories.map { it.id }.toSet()
    val fuelById = refuels.associateBy { it.id }
    require(expenses.all { it.categoryId in categoryIds }) { "Expense refers to a missing category." }
    val linked = expenses.filter { it.fuelId != null }
    require(linked.map { it.fuelId }.distinct().size == linked.size && linked.size == refuels.size) { "Fuel links are missing or duplicated." }
    linked.forEach { expense ->
        val fuel = fuelById[expense.fuelId]
        require(fuel != null && expense.categoryId == FUEL_CATEGORY && expense.cents == fuel.cents && expense.timestamp == fuel.timestamp &&
            expense.description == fuelDescription(fuel) && expense.notes == fuel.notes) { "Linked fuel expense is inconsistent." }
    }
    settings.validate()
}
fun fuelDescription(refuel: Refuel) = if (refuel.station.isBlank()) "Refuelling" else "Refuelling · ${refuel.station}"

/** Quote every field, including newlines and commas. Neutralize spreadsheet formula injection. */
fun csvCell(value: String): String {
    val safe = if (value.trimStart().firstOrNull() in listOf('=', '+', '-', '@', '\t', '\r')) "'$value" else value
    return "\"${safe.replace("\"", "\"\"")}\""
}
fun csv(rows: List<List<String>>) = "\uFEFF" + rows.joinToString("\r\n") { it.joinToString(",", transform = ::csvCell) } + "\r\n"
