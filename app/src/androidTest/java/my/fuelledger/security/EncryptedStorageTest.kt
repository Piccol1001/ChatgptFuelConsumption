package my.fuelledger.security

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase as PlatformDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import my.fuelledger.data.*
import net.zetetic.database.DatabaseErrorHandler
import net.zetetic.database.sqlcipher.SQLiteDatabase as CipherDatabase
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.KeyStore
import java.util.UUID
import javax.crypto.AEADBadTagException
import javax.crypto.spec.SecretKeySpec

/** Runs on Android: exercises the packaged native SQLCipher library and Android Keystore. */
@RunWith(AndroidJUnit4::class)
class EncryptedStorageTest {
    private lateinit var context: Context
    private lateinit var vault: LocalVault
    private val names = mutableListOf<String>()
    private val open = mutableListOf<LedgerDatabase>()
    private fun name() = "native-security-${UUID.randomUUID()}.db".also { names += it }
    private fun keyFile(name: String) = File(context.noBackupFilesDir, "keys/$name.key.enc")
    private fun candidate(name: String) = File(context.getDatabasePath(name).parentFile, "$name.encrypted-migrating")
    private fun database(name: String) = LedgerDatabase.open(context, vault, name).also { open += it }
    private fun close(database: LedgerDatabase) { database.close(); open.remove(database) }

    @Before fun setup() {
        context = ApplicationProvider.getApplicationContext()
        vault = LocalVault(context)
    }
    @After fun cleanup() {
        open.forEach { it.close() }
        names.forEach { name ->
            context.deleteDatabase(name)
            listOf(keyFile(name), File("${keyFile(name).path}.bak"), candidate(name),
                File("${candidate(name).path}-wal"), File("${candidate(name).path}-shm"),
                File("${candidate(name).path}-journal")).forEach { it.delete() }
        }
    }

    @Test fun plaintextVersionsOneThroughFourBecomeEncryptedVersionFiveWithoutLosingRecords() = runBlocking {
        for (version in 1..4) {
            val name = name()
            val (refuel, linked) = createPlaintext(name, version)
            val encrypted = database(name)
            assertEquals(refuel, encrypted.dao().allRefuels().single())
            assertEquals(linked, encrypted.dao().allExpenses().single { it.fuelId != null })
            assertEquals(2, encrypted.dao().allExpenses().size)
            assertEquals(defaultCategories.toSet(), encrypted.dao().allCategories().toSet())
            assertEquals("pending preferences preserved", encrypted.dao().pendingSettings()?.payload)
            assertEquals(5, encrypted.openHelper.readableDatabase.version)
            assertTrue(encrypted.financeDao().allIncome().isEmpty())
            val income = FinanceIncome("income", 123456, 5000, "Private income marker")
            encrypted.financeDao().putIncome(income)
            close(encrypted)

            val file = context.getDatabasePath(name)
            assertFalse(file.readBytes().copyOfRange(0, 16).contentEquals("SQLite format 3\u0000".toByteArray()))
            assertFalse(file.readBytes().toString(Charsets.ISO_8859_1).contains("Private income marker"))
            assertFalse(candidate(name).exists())
            val key = EncryptedDatabaseFiles.prepare(context, name, vault)
            try {
                assertFalse(keyFile(name).readBytes().toString(Charsets.ISO_8859_1).contains(key.toString(Charsets.US_ASCII)))
                assertCipherIntegrity(file, key)
            } finally { key.fill(0) }

            val reopened = database(name)
            assertEquals(refuel, reopened.dao().allRefuels().single())
            assertEquals(income, reopened.financeDao().allIncome().single())
            reopened.dao().deleteRefuel(refuel.id)
            assertEquals("manual", reopened.dao().allExpenses().single().id)
            close(reopened)
        }
    }

    @Test fun freshDatabasePersistsEncryptedRecordsWithKeystoreWrappedKey() = runBlocking {
        val name = name()
        val first = database(name)
        defaultCategories.forEach { first.dao().putCategory(it) }
        val expense = Expense("secret", 9900, "food", 1000, "Private expense marker",
            receiptFile = "12345678-abcd-1234-abcd-123456789abc.jpg")
        first.dao().putExpense(expense)
        close(first)
        assertTrue(keyFile(name).isFile)
        assertFalse(context.getDatabasePath(name).readBytes().toString(Charsets.ISO_8859_1).contains("Private expense marker"))
        val second = database(name)
        assertEquals(expense, second.dao().allExpenses().single())
        close(second)
    }

    @Test fun missingWrongWrappingKeyAndWrongDatabaseKeyNeverReplaceExistingRecords() = runBlocking {
        val name = name()
        createPlaintext(name, 2)
        val initial = database(name)
        val records = initial.dao().allExpenses()
        close(initial)
        val originalDatabase = context.getDatabasePath(name).readBytes()
        val originalWrappedKey = keyFile(name).readBytes()
        val wrongVault = LocalVault { SecretKeySpec(ByteArray(32) { 37 }, "AES") }
        expectFailure { LedgerDatabase.open(context, wrongVault, name) }
        assertArrayEquals(originalDatabase, context.getDatabasePath(name).readBytes())
        assertArrayEquals(originalWrappedKey, keyFile(name).readBytes())

        assertTrue(keyFile(name).delete())
        expectFailure { database(name) }
        assertFalse(keyFile(name).exists())
        assertArrayEquals(originalDatabase, context.getDatabasePath(name).readBytes())

        keyFile(name).writeBytes(vault.encrypt(ByteArray(64) { 'a'.code.toByte() }, "database-key:$name"))
        var wrong: LedgerDatabase? = null
        try { expectFailure { wrong = database(name); wrong!!.dao().allExpenses() } }
        finally { wrong?.let { close(it) } }
        assertArrayEquals(originalDatabase, context.getDatabasePath(name).readBytes())
        keyFile(name).writeBytes(originalWrappedKey)
        val recovered = database(name)
        assertEquals(records, recovered.dao().allExpenses())
        close(recovered)
    }

    @Test fun damagedPlaintextSourceFailsWithoutDeletingOrReplacingOriginal() = runBlocking {
        val name = name()
        createPlaintext(name, 1)
        val file = context.getDatabasePath(name)
        // Retain the plaintext header but remove required pages, forcing integrity/open failure.
        val damaged = file.readBytes().copyOf(256)
        file.writeBytes(damaged)
        expectFailure { database(name) }
        assertTrue(file.exists())
        assertArrayEquals(damaged, file.readBytes())
        assertFalse(candidate(name).exists())
    }

    @Test fun incompleteMigrationCandidateIsPreservedIfOriginalIsMissingOrEmpty() = runBlocking {
        for (emptyOriginal in listOf(false, true)) {
            val name = name()
            val path = context.getDatabasePath(name).apply { parentFile?.mkdirs() }
            if (emptyOriginal) path.writeBytes(ByteArray(0))
            val originalCandidate = "simulated interrupted encrypted export".toByteArray()
            candidate(name).writeBytes(originalCandidate)
            expectFailure { database(name) }
            assertArrayEquals(originalCandidate, candidate(name).readBytes())
            assertEquals(emptyOriginal, path.exists())
            if (path.exists()) assertEquals(0L, path.length())
        }
    }

    @Test fun keystoreMasterIsNonExportableAndPurposeBoundAcrossVaultInstances() {
        val plaintext = "Test-only confidential receipt".toByteArray()
        val encrypted = vault.encrypt(plaintext, "native-keystore:test")
        assertArrayEquals(plaintext, LocalVault(context).decrypt(encrypted, "native-keystore:test"))
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val key = store.getKey("${context.packageName}.local-vault.v1", null)
        assertNotNull(key)
        assertNull(key.encoded)
        try { vault.decrypt(encrypted, "native-keystore:other"); fail("Expected purpose authentication failure") }
        catch (_: AEADBadTagException) {}
    }

    private suspend fun expectFailure(block: suspend () -> Unit) {
        val failure = try { block(); null } catch (error: Exception) { error }
        assertNotNull("Operation should fail while preserving existing files", failure)
    }

    private fun assertCipherIntegrity(file: File, key: ByteArray) {
        CipherDatabase.openDatabase(file.path, key, null, CipherDatabase.OPEN_READONLY,
            DatabaseErrorHandler { _, error -> throw error }, null).use { database ->
            assertTrue(database.isDatabaseIntegrityOk)
            database.rawQuery("PRAGMA cipher_integrity_check", emptyArray<String>()).use { cursor ->
                while (cursor.moveToNext()) assertEquals("ok", cursor.getString(0).lowercase())
            }
        }
    }

    private fun createPlaintext(name: String, version: Int): Pair<Refuel, Expense> {
        val asset = "my.fuelledger.data.LedgerDatabase/$version.json"
        val schema = InstrumentationRegistry.getInstrumentation().context.assets.open(asset).bufferedReader().use {
            JSONObject(it.readText()).getJSONObject("database")
        }
        val refuel = Refuel("refuel", 1000, 12000.0, 25.0, 5125, false, "Private station", "Private notes",
            fuelType = if (version >= 2) "RON 95" else "",
            stationLocation = if (version >= 2) "Kuala Lumpur" else "",
            receiptFile = if (version >= 2) "12345678-abcd-1234-abcd-123456789abc.jpg" else null)
        val linked = Expense("linked", refuel.cents, FUEL_CATEGORY, refuel.timestamp,
            fuelDescription(refuel), refuel.notes, fuelId = refuel.id)
        val file = context.getDatabasePath(name).apply { parentFile?.mkdirs() }
        PlatformDatabase.openOrCreateDatabase(file, null).use { old ->
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                val table = entity.getString("tableName")
                old.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                entity.optJSONArray("indices")?.let { indices ->
                    for (j in 0 until indices.length()) old.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
                }
            }
            val setup = schema.getJSONArray("setupQueries")
            for (i in 0 until setup.length()) old.execSQL(setup.getString(i))
            defaultCategories.forEach { category ->
                old.execSQL("INSERT INTO categories VALUES (?, ?, ?, ?, ?)",
                    arrayOf<Any>(category.id, category.name, category.icon, category.color, if (category.custom) 1 else 0))
            }
            old.insertOrThrow("refuels", null, ContentValues().apply {
                put("id", refuel.id); put("timestamp", refuel.timestamp); put("odometer", refuel.odometer)
                put("litres", refuel.litres); put("cents", refuel.cents); put("fullTank", 0)
                put("station", refuel.station); put("notes", refuel.notes)
                if (version >= 2) {
                    put("fuelType", refuel.fuelType); put("stationLocation", refuel.stationLocation)
                    put("receiptFile", refuel.receiptFile)
                }
            })
            old.execSQL("INSERT INTO expenses (id, cents, categoryId, timestamp, description, notes, payment, fuelId) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                arrayOf<Any?>(linked.id, linked.cents, linked.categoryId, linked.timestamp,
                    linked.description, linked.notes, linked.payment, linked.fuelId))
            old.execSQL("INSERT INTO expenses (id, cents, categoryId, timestamp, description, notes, payment, fuelId) VALUES ('manual', 500, 'food', 2000, 'Manual', '', 'Cash', NULL)")
            old.execSQL("INSERT INTO pending_settings VALUES (1, 'pending preferences preserved')")
            old.version = version
        }
        return refuel to linked
    }
}
