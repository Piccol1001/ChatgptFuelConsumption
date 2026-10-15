package my.fuelledger.security

import android.content.Context
import android.database.sqlite.SQLiteDatabase as PlatformDatabase
import android.database.sqlite.SQLiteDatabaseCorruptException
import android.system.Os
import android.system.OsConstants
import android.util.AtomicFile
import net.zetetic.database.sqlcipher.SQLiteDatabase as CipherDatabase
import net.zetetic.database.DatabaseErrorHandler
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.SecureRandom

/** Runs before Room opens the database; never falls back to an unencrypted database. */
object EncryptedDatabaseFiles {
    private val plainHeader = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)
    private var libraryLoaded = false
    private val preserveCipherFiles = DatabaseErrorHandler { _, error -> throw error }

    @Synchronized
    fun prepare(context: Context, databaseName: String, vault: LocalVault): ByteArray {
        require(Regex("[A-Za-z0-9._-]{1,80}").matches(databaseName)) { "Invalid database file name." }
        if (!libraryLoaded) {
            System.loadLibrary("sqlcipher")
            libraryLoaded = true
        }
        val database = context.getDatabasePath(databaseName)
        check(database.parentFile?.let { it.isDirectory || it.mkdirs() } == true) { "Database directory is unavailable." }
        val lock = File(context.noBackupFilesDir, "database-migration.lock")
        RandomAccessFile(lock, "rw").channel.use { channel ->
            channel.lock().use {
                val key = loadKey(context, databaseName, database, vault)
                try {
                    val candidate = File(database.parentFile, "${database.name}.encrypted-migrating")
                    if ((!database.exists() || database.length() == 0L) && candidate.exists()) {
                        throw IOException("An incomplete database migration needs recovery. Existing files were preserved.")
                    }
                    if (database.isFile && database.length() > 0) {
                        if (isPlaintext(database)) encryptExisting(database, candidate, key)
                        else if (candidate.exists()) {
                            // A previous atomic replacement completed before its cleanup.
                            validateEncrypted(database, key, null)
                            deleteCandidate(candidate)
                        }
                    }
                    return key
                } catch (error: Throwable) {
                    key.fill(0)
                    throw error
                }
            }
        }
    }

    private fun loadKey(context: Context, name: String, database: File, vault: LocalVault): ByteArray {
        val keyDirectory = File(context.noBackupFilesDir, "keys")
        check(keyDirectory.isDirectory || keyDirectory.mkdirs()) { "Key directory is unavailable." }
        val keyFile = File(keyDirectory, "$name.key.enc")
        val atomic = AtomicFile(keyFile)
        try {
            atomic.openRead().use { input ->
                val encrypted = ByteArray(1025)
                var size = 0
                while (size < encrypted.size) {
                    val count = input.read(encrypted, size, encrypted.size - size)
                    if (count < 0) break
                    size += count
                }
                require(size in 1..1024) { "Invalid wrapped database key." }
                return vault.decrypt(encrypted.copyOf(size), "database-key:$name").also { key ->
                    require(key.size == 64 && key.all { it.toInt().toChar() in "0123456789abcdef" }) { "Invalid database key." }
                }
            }
        } catch (missing: FileNotFoundException) {
            if (database.isFile && database.length() > 0 && !isPlaintext(database)) {
                throw IOException("The encrypted database key is unavailable. Existing records were preserved.", missing)
            }
        }
        val entropy = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val alphabet = "0123456789abcdef"
        val key = ByteArray(64) { index ->
            val value = entropy[index / 2].toInt() and 255
            alphabet[if (index % 2 == 0) value ushr 4 else value and 15].code.toByte()
        }
        entropy.fill(0)
        try {
            val encrypted = vault.encrypt(key, "database-key:$name")
            val output = atomic.startWrite()
            try {
                output.write(encrypted)
                atomic.finishWrite(output)
            } catch (error: Throwable) {
                atomic.failWrite(output)
                throw error
            }
            syncDirectory(keyDirectory)
            return key
        } catch (error: Throwable) {
            key.fill(0)
            throw error
        }
    }

    private fun encryptExisting(database: File, candidate: File, key: ByteArray) {
        // The old database remains at its original name until export and validation succeed.
        val expected = checkpointPlaintext(database)
        deleteCandidate(candidate)
        try {
            // ATTACH inherits the source connection's no-create flag. Reserve an empty
            // private destination first, so the original is always opened READWRITE
            // without permitting SQLite to create a replacement source database.
            check(candidate.createNewFile()) { "Encrypted migration storage could not be prepared." }
            CipherDatabase.openDatabase(database.path, ByteArray(0), null,
                CipherDatabase.OPEN_READWRITE, preserveCipherFiles, null).use { source ->
                source.execSQL("ATTACH DATABASE ? AS encrypted KEY ?",
                    arrayOf<Any>(candidate.path, key.toString(Charsets.US_ASCII)))
                try {
                    source.rawQuery("SELECT sqlcipher_export('encrypted')", emptyArray<String>()).use { it.moveToFirst() }
                    source.execSQL("PRAGMA encrypted.user_version = ${expected.version}")
                } finally {
                    source.execSQL("DETACH DATABASE encrypted")
                }
            }
            validateEncrypted(candidate, key, expected)
            FileOutputStream(candidate, true).use { it.fd.sync() }
            // Same-filesystem atomic replacement leaves either the old plaintext database or
            // the fully verified encrypted database after interruption. No plaintext copy is made.
            Files.move(candidate.toPath(), database.toPath(), StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING)
            syncDirectory(database.parentFile!!)
        } catch (error: Throwable) {
            // If replacement did not happen, original records are still at database.path.
            // Preserve an encrypted candidate if the original unexpectedly disappeared.
            if (database.exists()) deleteCandidate(candidate)
            throw error
        }
    }

    private data class Snapshot(val version: Int, val tables: Map<String, Long>)

    private fun checkpointPlaintext(file: File): Snapshot = PlatformDatabase.openDatabase(
        file.path, null, PlatformDatabase.OPEN_READWRITE,
        { throw SQLiteDatabaseCorruptException("Existing database failed its integrity check; files were preserved.") }
    ).use { database ->
        database.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", null).use { cursor ->
            check(cursor.moveToFirst() && cursor.getInt(0) == 0) { "Database is busy; encryption was not started." }
        }
        database.rawQuery("PRAGMA journal_mode=DELETE", null).use { cursor ->
            check(cursor.moveToFirst() && cursor.getString(0).equals("delete", true)) { "Database journal could not be checkpointed." }
        }
        database.rawQuery("PRAGMA integrity_check", null).use { cursor ->
            check(cursor.moveToFirst() && cursor.getString(0) == "ok" && !cursor.moveToNext()) { "The existing database failed its integrity check." }
        }
        val tables = linkedMapOf<String, Long>()
        database.rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' ORDER BY name", null).use { cursor ->
            while (cursor.moveToNext()) {
                val table = cursor.getString(0)
                database.rawQuery("SELECT COUNT(*) FROM ${quote(table)}", null).use { count ->
                    check(count.moveToFirst())
                    tables[table] = count.getLong(0)
                }
            }
        }
        Snapshot(database.version, tables)
    }

    private fun validateEncrypted(file: File, key: ByteArray, expected: Snapshot?) {
        check(file.isFile && file.length() > 0 && !isPlaintext(file)) { "Encrypted database was not created." }
        CipherDatabase.openDatabase(file.path, key, null, CipherDatabase.OPEN_READONLY, preserveCipherFiles, null).use { database ->
            database.rawQuery("PRAGMA cipher_integrity_check", emptyArray<String>()).use { cursor ->
                while (cursor.moveToNext()) check(cursor.getString(0).equals("ok", true)) { "Encrypted database authentication failed." }
            }
            check(database.isDatabaseIntegrityOk) { "Encrypted database failed its integrity check." }
            if (expected != null) {
                check(database.version == expected.version) { "Database schema version changed during encryption." }
                val actual = linkedMapOf<String, Long>()
                database.rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' ORDER BY name", emptyArray<String>()).use { cursor ->
                    while (cursor.moveToNext()) {
                        val table = cursor.getString(0)
                        database.rawQuery("SELECT COUNT(*) FROM ${quote(table)}", emptyArray<String>()).use { count ->
                            check(count.moveToFirst())
                            actual[table] = count.getLong(0)
                        }
                    }
                }
                check(actual == expected.tables) { "Database records did not match after encryption." }
            }
        }
    }

    private fun quote(identifier: String) = "\"${identifier.replace("\"", "\"\"")}\""
    private fun isPlaintext(file: File): Boolean = FileInputStream(file).use { input ->
        val header = ByteArray(plainHeader.size)
        input.read(header) == header.size && header.contentEquals(plainHeader)
    }
    private fun deleteCandidate(file: File) {
        listOf(file, File("${file.path}-wal"), File("${file.path}-shm"), File("${file.path}-journal")).forEach {
            if (it.exists() && !it.delete()) throw IOException("An old encrypted migration file could not be removed.")
        }
    }
    private fun syncDirectory(directory: File) {
        val descriptor = Os.open(directory.path, OsConstants.O_RDONLY, 0)
        try { Os.fsync(descriptor) } finally { Os.close(descriptor) }
    }
}
