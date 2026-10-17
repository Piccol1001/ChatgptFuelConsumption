package my.fuelledger.data

import kotlinx.serialization.decodeFromString
import org.junit.Assert.*
import org.junit.Test

class ExpenseEntryTest {
    @Test fun manualCategoryChoicesOmitDedicatedFuelButKeepTransportParkingTollsAndHistoricalIds() {
        val transport = listOf("Transport", "Parking", "Tolls").map { Category(it.lowercase(), it, "fuel", 1L) }
        val choices = manualExpenseCategories(defaultCategories + transport)
        assertFalse(choices.any { it.id == FUEL_CATEGORY })
        assertTrue(choices.containsAll(transport)); assertEquals(OTHER_CATEGORY, choices.last().id)
        assertTrue(defaultCategories.any { it.id == FUEL_CATEGORY })
        assertEquals(listOf("E-wallet", "Cash", "Card"), expensePaymentOptions)
    }

    @Test fun cardDisplayGroupingPreservesHistoricalStringsUnlessUserChangesGroup() {
        for (raw in listOf("Credit Card", "Debit Card", "Card")) {
            assertEquals("Card", expensePaymentGroup(raw))
            assertEquals(raw, selectExpensePayment(raw, "Card"))
            assertEquals("Cash", selectExpensePayment(raw, "Cash"))
            validateExpense(Expense("legacy", 12345, "food", 1000, payment = raw))
        }
        assertEquals("E-wallet", selectExpensePayment("Not specified", "E-wallet"))
        assertNull(expensePaymentGroup("Not specified"))
        validateExpense(Expense("unknown", 12345, "food", 1000, payment = "Not specified"))
        assertFalse("Not specified" in expensePaymentOptions)
        assertTrue(runCatching { selectExpensePayment("Cash", "Not specified") }.isFailure)
    }
    @Test fun oldSettingsAndExpenseJsonKeepOriginalValuesAndSafeNewPreference() {
        val old = backupJson.decodeFromString<AppSettings>("""{"theme":"Dark","consumption":8.5}""")
        assertEquals("Cash", old.lastExpensePayment); assertEquals("Dark", old.theme)
        val expense = backupJson.decodeFromString<Expense>("""{"id":"old","cents":12345,"categoryId":"food","timestamp":1000,"payment":"Debit Card"}""")
        assertEquals(12345L, expense.cents); assertEquals("Debit Card", expense.payment)
        assertTrue(runCatching { old.copy(lastExpensePayment = "Not specified").validate() }.isFailure)
    }
}
