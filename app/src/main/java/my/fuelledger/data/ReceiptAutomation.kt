package my.fuelledger.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import my.fuelledger.receipts.ReceiptSuggestions
import my.fuelledger.domain.Money
import java.security.MessageDigest
import java.time.*
import java.util.Locale

fun merchantKey(text: String) = text.lowercase(Locale.ROOT).filter { it.isLetterOrDigit() }.take(200)
fun receiptFingerprint(text: String) = MessageDigest.getInstance("SHA-256").digest(
    text.uppercase(Locale.ROOT).filter { it.isLetterOrDigit() }.toByteArray()).joinToString("") { "%02x".format(it) }
@Serializable
@Entity(tableName = "receipt_merchants")
data class ReceiptMerchant(@PrimaryKey val merchant: String, val displayName: String, val categoryId: String,
    @ColumnInfo(defaultValue = "''") val payment: String = "",
    @ColumnInfo(defaultValue = "''") val description: String = "")
@Serializable
@Entity(tableName = "receipt_scans")
data class ReceiptScan(@PrimaryKey val fingerprint: String, val expenseId: String)
@Dao
interface ReceiptAutomationDao {
    @Query("SELECT * FROM receipt_merchants ORDER BY displayName") fun merchants(): Flow<List<ReceiptMerchant>>
    @Query("SELECT * FROM receipt_merchants ORDER BY merchant") suspend fun allMerchants(): List<ReceiptMerchant>
    @Query("SELECT * FROM receipt_scans ORDER BY fingerprint") suspend fun allScans(): List<ReceiptScan>
    @Query("SELECT * FROM receipt_scans WHERE fingerprint = :fingerprint") suspend fun scan(fingerprint: String): ReceiptScan?
    @Upsert suspend fun putMerchant(value: ReceiptMerchant)
    @Insert suspend fun putScan(value: ReceiptScan)
    @Query("DELETE FROM receipt_merchants WHERE merchant = :key") suspend fun deleteMerchant(key: String)
    @Query("DELETE FROM receipt_scans WHERE expenseId = :id") suspend fun undoScan(id: String)
    @Query("DELETE FROM receipt_merchants") suspend fun clearMerchants()
    @Query("DELETE FROM receipt_scans") suspend fun clearScans()
}
fun validateReceiptMerchant(value: ReceiptMerchant) {
    require((value.payment.isEmpty() || expensePaymentGroup(value.payment) != null) && value.description.length <= 200) { "Invalid merchant preferences." }
    require(value.displayName.isNotBlank() && value.displayName.length <= 200 && value.merchant == merchantKey(value.displayName) && value.merchant.isNotBlank() && value.categoryId.isNotBlank() && value.categoryId.length <= 100) { "Check the merchant and category." }
}
fun validateReceiptScan(value: ReceiptScan) {
    require(Regex("[0-9a-f]{64}").matches(value.fingerprint) && value.expenseId.isNotBlank() && value.expenseId.length <= 100) { "Invalid receipt history." }
}
fun receiptAutoSaveIssues(s: ReceiptSuggestions, categoryId: String?, duplicate: Boolean,
    today: LocalDate = LocalDate.now()): List<String> = buildList {
    s.imageQualityIssue?.let { add(it) }
    if (s.station.isNullOrBlank() || s.station.length > 200) add("Merchant is missing or unclear.")
    if (s.amount == null || runCatching { Money.parse(s.amount) }.isFailure || !s.strictTotal || s.ambiguousTotals) add("The final total needs review.")
    val date = s.date?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    if (date == null || date > today || date.year < 1970 || s.ambiguousDates) add("The transaction date needs review.")
    if (s.currency != "MYR") add("Only MYR receipts can be accepted; convert other currencies manually in Add Expense.")
    if (categoryId.isNullOrBlank()) add("Choose an expense category.")
    if (categoryId == FUEL_CATEGORY || s.fuelType != null) add("Petrol receipts need review against existing refuelling records.")
    if (duplicate) add("A matching receipt or transaction may already exist.")
}
fun possibleReceiptDuplicate(s: ReceiptSuggestions, expenses: List<Expense>, zone: ZoneId = ZoneId.systemDefault()): Boolean {
    val cents = s.amount?.let { runCatching { Money.parse(it) }.getOrNull() } ?: return false
    val date = s.date ?: return false
    return expenses.any {
        it.cents == cents && Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalDate().toString() == date
    }
}
