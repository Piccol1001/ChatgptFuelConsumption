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
            require(dao.expense(expense.id)?.fuelId == null) { "Edit linked expenses through the refuel record." }
            require(dao.allCategories().any { it.id == expense.categoryId }) { "Category no longer exists." }
            dao.putExpense(expense)
        }
    }
    suspend fun deleteExpense(id: String) = writes.withLock {
        database.withTransaction {
            require(dao.expense(id)?.fuelId == null) { "Delete this transaction through its refuel record." }
            dao.deleteExpense(id)
        }
    }
    suspend fun saveRefuel(refuel: Refuel) = writes.withLock {
        validateRefuel(refuel)
        database.withTransaction {
            val remaining = dao.allRefuels().filter { it.id != refuel.id }
            validateSequence((remaining + refuel).map { it.point() })
            val old = dao.linkedExpense(refuel.id)
            dao.putRefuel(refuel)
            dao.putExpense(Expense(id = old?.id ?: java.util.UUID.randomUUID().toString(), cents = refuel.cents,
                categoryId = FUEL_CATEGORY, timestamp = refuel.timestamp, description = fuelDescription(refuel),
                notes = refuel.notes, payment = old?.payment ?: "Cash", fuelId = refuel.id))
        }
    }
    suspend fun deleteRefuel(id: String) = writes.withLock {
        // SQLite CASCADE deletes exactly the linked expense in the same transaction.
        database.withTransaction { dao.deleteRefuel(id) }
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
            dao.deleteCategory(id)
        }
    }
    suspend fun saveSettings(settings: AppSettings) = writes.withLock {
        applyPendingSettings()
        preferences.save(settings)
    }
    suspend fun backup(): Backup = writes.withLock {
        applyPendingSettings()
        database.withTransaction {
            Backup(format = "my.fuelledger.backup", version = 1, createdAt = System.currentTimeMillis(), categories = dao.allCategories(),
                expenses = dao.allExpenses(), refuels = dao.allRefuels(), settings = preferences.settings.first())
        }
    }
    suspend fun restore(backup: Backup) = writes.withLock {
        backup.validate() // All validation occurs before deleting anything.
        database.withTransaction {
            dao.clearExpenses(); dao.clearRefuels(); dao.clearCategories()
            backup.categories.forEach { dao.putCategory(it) }
            backup.refuels.forEach { dao.putRefuel(it) }
            backup.expenses.forEach { dao.putExpense(it) }
            dao.putPendingSettings(PendingSettings(payload = backupJson.encodeToString(backup.settings)))
        }
        // If this write fails or the process stops, initialize() replays the committed intent.
        try { applyPendingSettings() }
        catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (error: Exception) {
            throw IllegalStateException("Records were restored. Preferences could not be applied yet; retry or reopen the app to finish restoring settings.", error)
        }
    }
    suspend fun exportExpenses(): String {
        val snapshot = backup()
        val names = snapshot.categories.associate { it.id to it.name }
        return csv(listOf(listOf("ID", "Date time (local, with offset)", "Amount (MYR)", "Category", "Description", "Notes", "Payment method", "Refuel ID")) +
            snapshot.expenses.map { listOf(it.id, localTimestamp(it.timestamp), my.fuelledger.domain.Money.decimal(it.cents),
                names[it.categoryId].orEmpty(), it.description, it.notes, it.payment, it.fuelId.orEmpty()) })
    }
    suspend fun exportRefuels(): String = csv(listOf(listOf("ID", "Date time (local, with offset)", "Odometer (km)", "Litres", "Payment (MYR)", "Price (MYR/L)", "Full tank", "Station", "Notes")) +
        backup().refuels.map { listOf(it.id, localTimestamp(it.timestamp), it.odometer.toString(), it.litres.toString(),
            my.fuelledger.domain.Money.decimal(it.cents), it.pricePerLitre.toString(), it.fullTank.toString(), it.station, it.notes) })
}
private fun localTimestamp(epoch: Long) = Instant.ofEpochMilli(epoch).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
