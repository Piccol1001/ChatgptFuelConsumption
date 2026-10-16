package my.fuelledger.data

import androidx.room.withTransaction
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import my.fuelledger.domain.validateSequence
import java.time.*
import java.time.format.DateTimeFormatter

/** Records committed successfully; initialize() will replay the persisted settings intent. */
class RestoreSettingsPendingException(cause: Exception) : IllegalStateException(
    "Records were restored. Preferences could not be applied yet; retry or reopen the app to finish restoring settings.", cause)

class LedgerRepository(val database: LedgerDatabase, val preferences: PreferencesStore) {
    val dao = database.dao()
    val categories = dao.categories()
    val expenses = dao.expenses()
    val refuels = dao.refuels()
    private val writes = Mutex()

    suspend fun initialize() = writes.withLock {
        database.withTransaction {
            val existing = dao.allCategories().map { it.id }.toSet()
            defaultCategories.filter { it.id !in existing }.forEach { dao.putCategory(it) }
        }
        applyPendingSettings()
    }
    private suspend fun applyPendingSettings() {
        dao.pendingSettings()?.let {
            preferences.save(backupJson.decodeFromString<AppSettings>(it.payload))
            dao.clearPendingSettings()
        }
    }
    suspend fun saveExpense(expense: Expense) = writes.withLock {
        validateExpense(expense)
        require(expense.fuelId == null) { "Edit linked expenses through the refuel record." }
        database.withTransaction {
            val existing = dao.expense(expense.id)
            require(existing?.fuelId == null) { "Edit linked expenses through the refuel record." }
            require(dao.allCategories().any { it.id == expense.categoryId }) { "Category no longer exists." }
            val updated = if (existing?.recurringRuleId != null) expense.copy(
                recurringRuleId = existing.recurringRuleId, recurringDate = existing.recurringDate) else expense
            validateExpense(updated)
            if (updated.kind == "Refund" && updated.refundOf != null) {
                val original = requireNotNull(dao.expense(updated.refundOf)) { "Original expense no longer exists. Choose an unlinked refund." }
                require(original.kind == "Expense" && original.categoryId == updated.categoryId) { "A linked refund must use the original category." }
                val refunded = dao.allExpenses().filter { it.id != updated.id && it.refundOf == original.id }.sumOf { -it.cents }
                require(refunded - updated.cents <= original.cents) { "Refunds exceed the original expense." }
            }
            if (existing != null && existing != updated) recordUndo(existing, updated)
            dao.putExpense(updated)
            if (updated.merchant.isNotBlank()) database.receiptAutomationDao().putMerchant(
                ReceiptMerchant(merchantKey(updated.merchant), updated.merchant, updated.categoryId, updated.payment, updated.description))
        }
    }
    suspend fun saveReceiptExpense(expense: Expense, suggestions: my.fuelledger.receipts.ReceiptSuggestions,
        fingerprint: String, rememberMerchant: Boolean, autoSave: Boolean, duplicateReviewed: Boolean = false) = writes.withLock {
        validateExpense(expense)
        require(expense.fuelId == null && expense.recurringRuleId == null && suggestions.currency == "MYR") { "Review the receipt in MYR before saving." }
        database.withTransaction {
            val automation = database.receiptAutomationDao()
            require(automation.scan(fingerprint) == null && dao.expense(expense.id) == null) { "This receipt has already been saved. Review the existing transaction." }
            require(duplicateReviewed || !possibleReceiptDuplicate(suggestions, dao.allExpenses())) { "A possible duplicate requires explicit review or linking to its existing transaction." }
            require(fingerprint == receiptFingerprint(suggestions.rawText)) { "Receipt identity changed; scan it again." }
            if (autoSave) {
                require(preferences.settings.first().receiptSaveMode == "Smart") { "Smart Auto-Save is disabled." }
                require(receiptAutoSaveIssues(suggestions, expense.categoryId,
                    possibleReceiptDuplicate(suggestions, dao.allExpenses())).isEmpty()) { "This receipt requires review." }
                val remembered = automation.allMerchants().firstOrNull { it.merchant == merchantKey(suggestions.station.orEmpty()) }
                require(remembered == null || remembered.categoryId == expense.categoryId) { "A saved merchant category changed. Review this receipt." }
                require(expense.cents == my.fuelledger.domain.Money.parse(suggestions.amount!!) &&
                    java.time.Instant.ofEpochMilli(expense.timestamp).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString() == suggestions.date &&
                    merchantKey(expense.description) == merchantKey(suggestions.station!!)) { "Edited receipt fields require review." }
            }
            require(dao.allCategories().any { it.id == expense.categoryId }) { "Category no longer exists." }
            validateReceiptScan(ReceiptScan(fingerprint, expense.id))
            dao.putExpense(expense)
            automation.putScan(ReceiptScan(fingerprint, expense.id))
            if (rememberMerchant) {
                val value = ReceiptMerchant(merchantKey(expense.description), expense.description, expense.categoryId, expensePaymentGroup(expense.payment).orEmpty(), expense.description)
                validateReceiptMerchant(value); automation.putMerchant(value)
            }
        }
    }
    suspend fun attachScannedReceipt(expenseId: String, photo: String?, fingerprint: String) = writes.withLock {
        database.withTransaction {
            val expense = requireNotNull(dao.expense(expenseId)) { "The selected expense no longer exists." }
            val scans = database.receiptAutomationDao()
            val prior = scans.scan(fingerprint)
            require(prior == null || prior.expenseId == expenseId) { "This receipt already belongs to another transaction." }
            if (photo != null) {
                expense.fuelId?.let { id ->
                    val fuel = requireNotNull(dao.allRefuels().firstOrNull { it.id == id })
                    dao.putRefuel(fuel.copy(receiptFile = photo))
                }
                dao.putExpense(expense.copy(receiptFile = photo))
            }
            if (prior == null) scans.putScan(ReceiptScan(fingerprint, expenseId).also(::validateReceiptScan))
        }
    }
    suspend fun undoReceiptExpense(expense: Expense) = writes.withLock {
        database.withTransaction {
            require(dao.expense(expense.id) == expense) { "The transaction has changed. Review it in Expenses before deleting." }
            dao.deleteExpense(expense.id)
            database.receiptAutomationDao().undoScan(expense.id)
        }
    }
    suspend fun deleteExpense(id: String) = writes.withLock {
        database.withTransaction {
            require(dao.expense(id)?.fuelId == null) { "Delete this transaction through its refuel record." }
            dao.expense(id)?.let { recordUndo(it, null) }
            dao.deleteExpense(id)
        }
    }
    private suspend fun recordUndo(before: Expense, after: Expense?) {
        database.toolsDao().putUndo(TransactionUndo(java.util.UUID.randomUUID().toString(), System.currentTimeMillis(), backupJson.encodeToString(TransactionChange(before, after))))
    }
    suspend fun undoTransaction(id: String) = writes.withLock {
        database.withTransaction {
            val entry = requireNotNull(database.toolsDao().undo(id)) { "Undo is no longer available." }
            val change = entry.change()
            require(dao.expense(change.before.id) == change.after) { "This transaction changed again. Review it before restoring." }
            require(dao.allCategories().any { it.id == change.before.categoryId }) { "The original category is no longer available." }
            change.beforeRefuel?.let { refuel ->
                require(dao.allRefuels().find { it.id == refuel.id } == change.afterRefuel) { "The refuel has changed again." }
                validateSequence((dao.allRefuels().filter { it.id != refuel.id } + refuel).map { it.point() })
                dao.putRefuel(refuel)
            }
            if (change.before.kind == "Refund" && change.before.refundOf != null) {
                val original = dao.expense(change.before.refundOf)
                if (original != null) require(-change.before.cents + dao.allExpenses().filter { it.id != change.before.id && it.refundOf == original.id }.sumOf { -it.cents } <= original.cents) { "Restoring would exceed the original expense's refundable amount." }
            }
            dao.putExpense(change.before)
            database.toolsDao().deleteUndo(id)
        }
    }
    suspend fun saveFavourite(value: FavouriteExpense) = writes.withLock {
        validateFavourite(value)
        require(dao.allCategories().any { it.id == value.expense().categoryId }) { "Category no longer exists." }
        database.toolsDao().putFavourite(value)
    }
    suspend fun deleteFavourite(id: String) = writes.withLock { database.toolsDao().deleteFavourite(id) }
    suspend fun removeReceiptImages() = writes.withLock {
        database.withTransaction {
            dao.allRefuels().filter { it.receiptFile != null }.forEach { dao.putRefuel(it.copy(receiptFile = null)) }
            dao.allExpenses().filter { it.receiptFile != null }.forEach { dao.putExpense(it.copy(receiptFile = null)) }
            database.toolsDao().allUndo().forEach { entry ->
                val change = entry.change()
                database.toolsDao().putUndo(entry.copy(payload = backupJson.encodeToString(change.copy(before = change.before.copy(receiptFile = null), after = change.after?.copy(receiptFile = null), beforeRefuel = change.beforeRefuel?.copy(receiptFile = null), afterRefuel = change.afterRefuel?.copy(receiptFile = null)))))
            }
        }
    }
    suspend fun clearUndoHistory() = writes.withLock { database.toolsDao().clearUndo() }
    suspend fun deleteHistorical(start: LocalDate, end: LocalDate, zone: ZoneId) = writes.withLock {
        require(end >= start) { "Choose a valid date range." }
        database.withTransaction {
            dao.allExpenses().filter { Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalDate() in start..end }.forEach {
                if (it.fuelId != null) deleteRefuelWithUndo(it.fuelId) else { recordUndo(it, null); dao.deleteExpense(it.id) }
            }
        }
    }
    suspend fun saveRefuel(refuel: Refuel) = writes.withLock {
        validateRefuel(refuel)
        database.withTransaction {
            val remaining = dao.allRefuels().filter { it.id != refuel.id }
            validateSequence((remaining + refuel).map { it.point() })
            val old = dao.linkedExpense(refuel.id)
            val oldRefuel = dao.allRefuels().find { it.id == refuel.id }
            dao.putRefuel(refuel)
            val updated = Expense(id = old?.id ?: java.util.UUID.randomUUID().toString(), cents = refuel.cents,
                categoryId = FUEL_CATEGORY, timestamp = refuel.timestamp, description = fuelDescription(refuel),
                notes = refuel.notes, payment = old?.payment ?: "Cash", fuelId = refuel.id, receiptFile = refuel.receiptFile)
            dao.putExpense(updated)
            if (old != null && oldRefuel != null && (old != updated || oldRefuel != refuel)) database.toolsDao().putUndo(TransactionUndo(java.util.UUID.randomUUID().toString(), System.currentTimeMillis(), backupJson.encodeToString(TransactionChange(old, updated, oldRefuel, refuel))))
        }
    }
    suspend fun deleteRefuel(id: String) = writes.withLock {
        // SQLite CASCADE deletes exactly the linked expense in the same transaction.
        database.withTransaction { deleteRefuelWithUndo(id) }
    }
    private suspend fun deleteRefuelWithUndo(id: String) {
        val refuel = dao.allRefuels().find { it.id == id }
        val expense = dao.linkedExpense(id)
        if (refuel != null && expense != null) database.toolsDao().putUndo(TransactionUndo(java.util.UUID.randomUUID().toString(), System.currentTimeMillis(), backupJson.encodeToString(TransactionChange(expense, null, refuel, null))))
        dao.deleteRefuel(id)
    }
    suspend fun saveCategory(category: Category) = writes.withLock {
        validateCategory(category)
        require(category.custom && category.id !in defaultCategories.map { it.id }) { "Built-in categories cannot be changed." }
        database.withTransaction {
            require(dao.allCategories().none { it.id != category.id && it.name.equals(category.name, true) }) { "Category name already exists." }
            dao.putCategory(category.copy(name = category.name.trim()))
        }
    }
    suspend fun deleteCategory(id: String) = writes.withLock {
        database.withTransaction {
            require(dao.allCategories().firstOrNull { it.id == id }?.custom == true) { "Built-in categories cannot be deleted." }
            dao.reassignCategory(id, OTHER_CATEGORY)
            FinanceRepository(database).reassignCategory(id, OTHER_CATEGORY)
            for (favourite in database.toolsDao().allFavourites().filter { it.expense().categoryId == id }) {
                database.toolsDao().putFavourite(favourite.copy(payload = backupJson.encodeToString(favourite.expense().copy(categoryId = OTHER_CATEGORY))))
            }
            for (merchant in database.receiptAutomationDao().allMerchants().filter { it.categoryId == id }) {
                database.receiptAutomationDao().putMerchant(merchant.copy(categoryId = OTHER_CATEGORY))
            }
            dao.deleteCategory(id)
        }
    }
    suspend fun saveSettings(settings: AppSettings) = writes.withLock {
        applyPendingSettings()
        preferences.save(settings)
    }
    suspend fun rememberExpensePayment(payment: String) = writes.withLock {
        require(payment in expensePaymentOptions) { "Choose Cash, Card or E-Wallet." }
        applyPendingSettings()
        val current = preferences.settings.first()
        if (current.lastExpensePayment != payment) preferences.save(current.copy(lastExpensePayment = payment))
    }
    suspend fun backup(): Backup = writes.withLock {
        applyPendingSettings()
        database.withTransaction {
            val finance = database.financeDao()
            Backup(format = "my.fuelledger.backup", version = 5, createdAt = System.currentTimeMillis(), categories = dao.allCategories(),
                expenses = dao.allExpenses(), refuels = dao.allRefuels(), settings = preferences.settings.first(),
                incomes = finance.allIncome(), budgets = finance.allBudgets(), recurring = finance.allRecurring(), plannerMonths = database.plannerDao().allMonths(), allocations = database.plannerDao().allAllocations(), journeys = database.journeyDao().allJourneys(), receiptMerchants = database.receiptAutomationDao().allMerchants(), receiptScans = database.receiptAutomationDao().allScans(), favourites = database.toolsDao().allFavourites())
        }
    }
    suspend fun restore(backup: Backup) = writes.withLock {
        backup.validate() // All validation occurs before deleting anything.
        database.withTransaction {
            val finance = database.financeDao()
            database.toolsDao().clearFavourites(); database.toolsDao().clearUndo()
            finance.clearIncome(); finance.clearBudgets(); finance.clearRecurring()
            database.plannerDao().clearMonths(); database.plannerDao().clearAllocations(); database.journeyDao().clear()
            database.receiptAutomationDao().clearMerchants(); database.receiptAutomationDao().clearScans()
            dao.clearExpenses(); dao.clearRefuels(); dao.clearCategories()
            backup.categories.forEach { dao.putCategory(it) }
            backup.refuels.forEach { dao.putRefuel(it) }
            backup.expenses.forEach { dao.putExpense(it) }
            backup.favourites.forEach { database.toolsDao().putFavourite(it) }
            backup.incomes.forEach { finance.putIncome(it) }
            backup.budgets.forEach { finance.putBudget(it) }
            backup.recurring.forEach { finance.putRecurring(it) }
            backup.plannerMonths.forEach { database.plannerDao().putMonth(it) }
            backup.allocations.forEach { database.plannerDao().putAllocation(it) }
            backup.journeys.forEach { database.journeyDao().put(it) }
            backup.receiptMerchants.forEach { database.receiptAutomationDao().putMerchant(it) }
            backup.receiptScans.forEach { database.receiptAutomationDao().putScan(it) }
            dao.putPendingSettings(PendingSettings(payload = backupJson.encodeToString(backup.settings)))
        }
        // If this write fails or the process stops, initialize() replays the committed intent.
        try { applyPendingSettings() }
        catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (error: Exception) {
            throw RestoreSettingsPendingException(error)
        }
    }
    suspend fun exportExpenses(): String {
        val snapshot = backup()
        val names = snapshot.categories.associate { it.id to it.name }
        return csv(listOf(listOf("ID", "Date time (local, with offset)", "Amount (MYR)", "Category", "Description", "Notes", "Payment method", "Refuel ID", "Receipt file", "Recurring rule ID", "Recurring date", "Merchant", "Type", "Original transaction")) +
            snapshot.expenses.map { listOf(it.id, localTimestamp(it.timestamp), my.fuelledger.domain.Money.decimal(it.cents),
                names[it.categoryId].orEmpty(), it.description, it.notes, it.payment, it.fuelId.orEmpty(),
                it.receiptFile.orEmpty(), it.recurringRuleId.orEmpty(), it.recurringDate.orEmpty(), it.merchant, it.kind, it.refundOf.orEmpty()) })
    }
    suspend fun exportRefuels(): String = csv(listOf(listOf("ID", "Date time (local, with offset)", "Odometer (km)", "Litres", "Payment (MYR)", "Price (MYR/L)", "Full tank", "Station", "Notes", "Fuel type", "Station location", "Receipt file")) +
        backup().refuels.map { listOf(it.id, localTimestamp(it.timestamp), it.odometer.toString(), it.litres.toString(),
            my.fuelledger.domain.Money.decimal(it.cents), it.pricePerLitre.toString(), it.fullTank.toString(), it.station, it.notes,
            it.fuelType, it.stationLocation, it.receiptFile.orEmpty()) })
}
private fun localTimestamp(epoch: Long) = Instant.ofEpochMilli(epoch).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
