package my.fuelledger.data

val expensePaymentOptions = listOf("E-wallet", "Cash", "Card")

/** Presentation grouping only. Never migrate or rewrite historical payment strings. */
fun expensePaymentGroup(payment: String): String? = when (payment) {
    "Cash" -> "Cash"
    "Card", "Credit Card", "Debit Card" -> "Card"
    "E-wallet" -> "E-wallet"
    else -> null
}

fun selectExpensePayment(current: String, selection: String): String {
    require(selection in expensePaymentOptions)
    return if (expensePaymentGroup(current) == selection) current else selection
}

/** Hide only the dedicated fuel category. Existing category IDs and history stay intact. */
fun manualExpenseCategories(categories: List<Category>): List<Category> =
    displayCategories(categories).filterNot { it.id == FUEL_CATEGORY }
