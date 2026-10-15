package my.fuelledger.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import my.fuelledger.domain.*
import java.time.LocalDate

val backupJson = Json { prettyPrint = true; encodeDefaults = true; ignoreUnknownKeys = false }
@Serializable
data class Backup(val format: String, val version: Int,
    val createdAt: Long, val categories: List<Category>, val expenses: List<Expense>,
    val refuels: List<Refuel>, val settings: AppSettings,
    val incomes: List<FinanceIncome> = emptyList(), val budgets: List<FinanceBudget> = emptyList(),
    val recurring: List<FinanceRecurring> = emptyList(),
    val plannerMonths: List<FinanceMonth> = emptyList(), val allocations: List<AllocationPlan> = emptyList(),
    val journeys: List<SavedJourney> = emptyList(),
    val receiptMerchants: List<ReceiptMerchant> = emptyList(), val receiptScans: List<ReceiptScan> = emptyList())

private val receiptFileName = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.jpg")
private fun validateReceiptFile(name: String?) {
    require(name == null || receiptFileName.matches(name)) { "Invalid receipt file name." }
}

fun validateExpense(expense: Expense) {
    require(expense.id.isNotBlank() && expense.id.length <= 100) { "Invalid expense ID." }
    require(expense.timestamp >= 0 && expense.timestamp <= 32_503_680_000_000L) { "Invalid expense date." }
    require(expense.cents in 1..MAX_CENTS) { "Amount must be positive." }
    require(expense.description.length <= 200 && expense.notes.length <= 2000) { "Description or notes are too long." }
    require(expense.payment in paymentMethods) { "Choose a valid payment method." }
    require(expense.fuelId == null || (expense.fuelId.isNotBlank() && expense.fuelId.length <= 100)) { "Invalid linked refuel ID." }
    validateReceiptFile(expense.receiptFile)
    require((expense.recurringRuleId == null) == (expense.recurringDate == null)) { "Recurring payment metadata is incomplete." }
    expense.recurringRuleId?.let { id ->
        require(id.isNotBlank() && id.length <= 64 && expense.fuelId == null) { "Invalid recurring payment reference." }
        val date = LocalDate.parse(requireNotNull(expense.recurringDate))
        require(date.year in 1970..3000 && date.toString() == expense.recurringDate) { "Invalid recurring payment date." }
    }
}
fun validateRefuel(refuel: Refuel) {
    require(refuel.id.isNotBlank() && refuel.id.length <= 100 && refuel.timestamp in 0..32_503_680_000_000L) { "Invalid refuel ID or date." }
    require(refuel.station.length <= 100 && refuel.notes.length <= 2000) { "Station or notes are too long." }
    require(refuel.fuelType.length <= 50 && refuel.stationLocation.length <= 200) { "Fuel type or station location is too long." }
    validateReceiptFile(refuel.receiptFile)
    validateSequence(listOf(refuel.point()))
}
fun validateCategory(category: Category) {
    require(category.id.isNotBlank() && category.id.length <= 100 && category.name.isNotBlank() && category.name.length <= 50) { "Category name must contain 1–50 characters." }
    require(category.icon in categoryIcons && category.color in categoryColors) { "Choose a supported icon and colour." }
}
fun Backup.validate() {
    require(format == "my.fuelledger.backup" && version in 1..4) { "This is not a compatible Fuel & Expense Tracker backup." }
    require(createdAt in 0..32_503_680_000_000L) { "Invalid backup creation date." }
    require(categories.size in 7..500 && expenses.size <= 100_000 && refuels.size <= 100_000 &&
        incomes.size <= 100_000 && budgets.size <= 100_000 && recurring.size <= 100_000) {
        "Backup contains too many records or is missing default categories."
    }
    require(categories.map { it.id }.distinct().size == categories.size &&
        expenses.map { it.id }.distinct().size == expenses.size && refuels.map { it.id }.distinct().size == refuels.size &&
        incomes.map { it.id }.distinct().size == incomes.size && budgets.map { it.id }.distinct().size == budgets.size &&
        recurring.map { it.id }.distinct().size == recurring.size) { "Backup contains duplicate IDs." }
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
    require(plannerMonths.size <= 12360 && allocations.size <= 12361 && plannerMonths.map { it.month }.distinct().size == plannerMonths.size && allocations.map { it.scope }.distinct().size == allocations.size) { "Invalid planner history." }
    require(journeys.size <= 100000 && journeys.map { it.id }.distinct().size == journeys.size) { "Invalid saved journeys." }
    require(receiptMerchants.size <= 10000 && receiptScans.size <= 100000 && receiptMerchants.map { it.merchant }.distinct().size == receiptMerchants.size && receiptScans.map { it.fingerprint }.distinct().size == receiptScans.size) { "Invalid receipt mapping or scan history." }
    receiptMerchants.forEach(::validateReceiptMerchant)
    receiptScans.forEach(::validateReceiptScan)
    require(receiptMerchants.all { it.categoryId in categoryIds }) { "Receipt merchant category is missing." }
    journeys.forEach(::validateSavedJourney)
    plannerMonths.forEach(::validateFinanceMonth)
    allocations.forEach(::validateAllocationPlan)
    incomes.forEach(::validateFinanceIncome)
    budgets.forEach(::validateFinanceBudget)
    recurring.forEach(::validateFinanceRecurring)
    require(budgets.all { it.categoryId.isEmpty() || it.categoryId in categoryIds } &&
        recurring.all { it.kind == "Income" || it.categoryId in categoryIds }) { "A financial record refers to a missing category." }
    require(budgets.map { it.month to it.categoryId }.distinct().size == budgets.size) {
        "Backup contains duplicate budgets for a month and category."
    }
    val occurrences = expenses.filter { it.recurringRuleId != null }.map { it.recurringRuleId to it.recurringDate }
    require(occurrences.distinct().size == occurrences.size) { "Backup contains duplicate recurring payment occurrences." }
    // A rule may have been deleted after a payment was created. Keep that historical provenance.
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
