package my.fuelledger.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import my.fuelledger.receipts.ReceiptStore
import my.fuelledger.security.LocalVault
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import javax.crypto.spec.SecretKeySpec
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReceiptBackupArchiveTest {
    private lateinit var context: Context
    private lateinit var store: ReceiptStore
    private lateinit var archive: ReceiptBackupArchive
    private lateinit var vault: LocalVault
    private val first = "12345678-abcd-1234-abcd-123456789abc.jpg"
    private val second = "23456789-abcd-1234-abcd-123456789abc.jpg"

    @Before fun setup() {
        context = ApplicationProvider.getApplicationContext()
        vault = LocalVault { SecretKeySpec(ByteArray(32) { (it + 11).toByte() }, "AES") }
        store = ReceiptStore(context, vault)
        archive = ReceiptBackupArchive(context, store, vault)
        store.file(first).parentFile?.deleteRecursively()
        restoreStages().forEach { it.deleteRecursively() }
    }

    @After fun cleanup() {
        store.file(first).parentFile?.deleteRecursively()
        restoreStages().forEach { it.deleteRecursively() }
    }

    private fun backup(names: List<String?> = listOf(null)): Backup {
        val refuels = names.mapIndexed { i, name ->
            Refuel("refuel-$i", 1000L + i * 1000, 1000.0 + i * 100, 10.0, 2050, false,
                station = "Petronas", fuelType = "RON 95", stationLocation = "Kuala Lumpur", receiptFile = name)
        }
        val expenses = refuels.map {
            Expense("expense-${it.id}", it.cents, FUEL_CATEGORY, it.timestamp,
                fuelDescription(it), it.notes, fuelId = it.id)
        }
        return Backup("my.fuelledger.backup", 2, 1000, defaultCategories, expenses, refuels, AppSettings())
    }

    private fun jpeg(): ByteArray = ByteArrayOutputStream().also { output ->
        Bitmap.createBitmap(8, 12, Bitmap.Config.ARGB_8888).also { image ->
            image.eraseColor(Color.WHITE)
            check(image.compress(Bitmap.CompressFormat.JPEG, 90, output))
            image.recycle()
        }
    }.toByteArray()

    private suspend fun savePhoto(name: String, image: ByteArray = jpeg()): File {
        store.writePhoto(name, image)
        return store.file(name)
    }

    private fun zip(backup: Backup, vararg entries: Pair<String, ByteArray>): ByteArray =
        zipEntries(listOf("manifest.json" to backupJson.encodeToString(backup).toByteArray()) + entries)

    private fun zipEntries(entries: List<Pair<String, ByteArray>>): ByteArray = ByteArrayOutputStream().also { output ->
        ZipOutputStream(output).use { zip ->
            entries.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
    }.toByteArray()

    private fun restoreStages() = context.cacheDir.listFiles().orEmpty().filter { it.name.startsWith("receipt-restore-") }
    private fun liveFiles() = store.file(first).parentFile?.listFiles().orEmpty().associate { it.name to it.readBytes().toList() }
    private suspend fun reject(bytes: ByteArray, messagePart: String) {
        val before = liveFiles()
        try {
            archive.read(ByteArrayInputStream(bytes)).close()
            fail("Expected invalid backup rejection")
        } catch (failure: IllegalArgumentException) {
            assertTrue("Unexpected message: ${failure.message}", failure.message.orEmpty().contains(messagePart))
        }
        assertEquals(before, liveFiles())
        assertTrue("Rejected archives must remove all staging files", restoreStages().isEmpty())
    }

    @Test fun zipRoundTripIncludesPhotosAndRestoresToFreshNamesOnlyAfterConfirmation() = runBlocking {
        val image = jpeg()
        savePhoto(first, image)
        val original = backup(listOf(first)).copy(version = 3)
        val output = ByteArrayOutputStream()
        archive.writeEncrypted(output, original, "Roundtrip backup password".toCharArray())
        val entryNames = mutableListOf<String>()
        BackupCrypto.decryptingStream(ByteArrayInputStream(output.toByteArray()), "Roundtrip backup password".toCharArray()).use { decrypted ->
            ZipInputStream(decrypted).use { zip ->
                while (true) entryNames += (zip.nextEntry ?: break).name
            }
        }
        assertEquals(listOf("manifest.json", "receipts/$first"), entryNames)
        val before = liveFiles()
        archive.readEncrypted(ByteArrayInputStream(output.toByteArray()), "Roundtrip backup password".toCharArray()).use { prepared ->
            assertEquals(original, prepared.backup)
            assertEquals(1, prepared.receiptCount)
            assertEquals(before, liveFiles())
            val restored = archive.materialize(prepared)
            val newName = requireNotNull(restored.refuels.single().receiptFile)
            assertNotEquals(first, newName)
            assertEquals(original.refuels.single().copy(receiptFile = newName), restored.refuels.single())
            assertEquals(original.expenses, restored.expenses)
            assertArrayEquals(image, store.readPhoto(newName))
            assertArrayEquals(image, store.readPhoto(first))
            restored.validate()
        }
        assertTrue(restoreStages().isEmpty())
    }

    @Test fun closingPreviewDoesNotWritePrivateOrGalleryPhotos() = runBlocking {
        val bytes = zip(backup(listOf(first)), "receipts/$first" to jpeg())
        archive.read(ByteArrayInputStream(bytes)).use { prepared ->
            assertEquals(1, prepared.receiptCount)
            assertTrue(liveFiles().isEmpty())
            assertTrue(prepared.stagingDirectory.isDirectory)
        }
        assertTrue(liveFiles().isEmpty())
        assertTrue(restoreStages().isEmpty())
    }

    @Test fun legacyVersionOneJsonRetainsRecordsAndDefaultsForNewMetadata() = runBlocking {
        val json = JSONObject(backupJson.encodeToString(backup())).apply {
            put("version", 1)
            remove("incomes"); remove("budgets"); remove("recurring")
            getJSONArray("refuels").getJSONObject(0).apply {
                remove("fuelType"); remove("stationLocation"); remove("receiptFile")
            }
        }.toString()
        archive.read(ByteArrayInputStream(json.toByteArray())).use { prepared ->
            assertEquals(1, prepared.backup.version)
            assertTrue(prepared.backup.incomes.isEmpty() && prepared.backup.budgets.isEmpty() && prepared.backup.recurring.isEmpty())
            assertEquals(0, prepared.receiptCount)
            assertNull(prepared.backup.refuels.single().receiptFile)
            assertEquals("", prepared.backup.refuels.single().fuelType)
            assertEquals("", prepared.backup.refuels.single().stationLocation)
            val restored = archive.materialize(prepared)
            assertEquals(prepared.backup, restored)
        }
        assertTrue(restoreStages().isEmpty())
        assertTrue(liveFiles().isEmpty())
    }

    @Test fun standaloneVersionTwoJsonWithoutPhotosIsSupported() = runBlocking {
        val original = backup()
        archive.read(ByteArrayInputStream(backupJson.encodeToString(original).toByteArray())).use {
            assertEquals(original, it.backup)
            assertEquals(0, it.receiptCount)
        }
        assertTrue(restoreStages().isEmpty())
    }

    @Test fun missingReceiptInZipOrJsonIsRejectedBeforeLiveMutation() = runBlocking {
        savePhoto(second)
        reject(zip(backup(listOf(first))), "missing one or more receipt")
        reject(backupJson.encodeToString(backup(listOf(first))).toByteArray(), "complete ZIP backup")
    }

    @Test fun traversalAndUnreferencedEntriesAreRejected() = runBlocking {
        val image = jpeg()
        listOf("../$first", "receipts/../$first", "/receipts/$first", "receipts\\$first", "other.txt").forEach {
            reject(zip(backup(), it to image), "unsafe or unsupported")
        }
        reject(zip(backup(), "receipts/$first" to image), "unreferenced receipt")
        reject(zip(backup(), "receipts/" to byteArrayOf()), "directory entry")
    }

    @Test fun duplicateReceiptEntriesAreRejected() = runBlocking {
        val bytes = zip(backup(listOf(first)), "receipts/$first" to jpeg(), "receipts/$second" to jpeg())
        val from = second.toByteArray()
        val to = first.toByteArray()
        for (offset in 0..bytes.size - from.size) {
            if (from.indices.all { bytes[offset + it] == from[it] }) to.copyInto(bytes, offset)
        }
        reject(bytes, "duplicate archive entry")
    }

    @Test fun invalidMetadataAndInvalidJpegNeverBecomeLiveFiles() = runBlocking {
        savePhoto(second)
        reject(zip(backup(listOf(first)).copy(categories = emptyList()), "receipts/$first" to jpeg()), "default categories")
        reject(zip(backup(listOf(first)), "receipts/$first" to "not a JPEG".toByteArray()), "not a JPEG")
        reject(zip(backup(listOf(first)), "receipts/$first" to jpeg().dropLast(2).toByteArray()), "incomplete")
        val png = ByteArrayOutputStream().also { output ->
            Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888).also {
                it.compress(Bitmap.CompressFormat.PNG, 100, output)
                it.recycle()
            }
        }.toByteArray()
        reject(zip(backup(listOf(first)), "receipts/$first" to png), "not a JPEG")
    }

    @Test fun oversizedReceiptIsRejectedAndStagingRemoved() = runBlocking {
        reject(zip(backup(listOf(first)), "receipts/$first" to ByteArray(8 * 1024 * 1024 + 1)), "size limit")
    }

    @Test fun oversizedJsonIsBoundedWithoutReadingWholeInput() = runBlocking {
        var read = 0
        val source = object : InputStream() {
            override fun read(): Int { read++; return ' '.code }
            override fun read(buffer: ByteArray, off: Int, len: Int): Int {
                read += len
                buffer.fill(' '.code.toByte(), off, off + len)
                return len
            }
        }
        try {
            archive.read(source).close()
            fail("Expected metadata size rejection")
        } catch (failure: IllegalArgumentException) {
            assertTrue(failure.message.orEmpty().contains("size limit"))
        }
        assertTrue(read <= 16 * 1024 * 1024 + 32 * 1024)
        assertTrue(restoreStages().isEmpty())
        assertTrue(liveFiles().isEmpty())
    }

    @Test fun cancellationCleansPreviewStagingAndLeavesPrivatePhotosUnchanged() = runBlocking {
        savePhoto(first)
        val before = liveFiles()
        val source = object : InputStream() {
            private var firstRead = true
            override fun read(): Int = throw CancellationException("Cancelled selection")
            override fun read(buffer: ByteArray, off: Int, len: Int): Int {
                if (!firstRead) throw CancellationException("Cancelled selection")
                firstRead = false
                "{   ".toByteArray().copyInto(buffer, off)
                return 4
            }
        }
        try {
            archive.read(source).close()
            fail("Expected cancelled selection")
        } catch (_: CancellationException) { }
        assertEquals(before, liveFiles())
        assertTrue(restoreStages().isEmpty())
    }

    @Test fun failedMaterializationRemovesOnlyFreshCopiesAndClosesStaging() = runBlocking {
        savePhoto(first)
        val before = liveFiles()
        val prepared = archive.read(ByteArrayInputStream(zip(backup(listOf(first, second)),
            "receipts/$first" to jpeg(), "receipts/$second" to jpeg())))
        prepared.photos.getValue(second).delete()
        try {
            archive.materialize(prepared)
            fail("Expected missing staged receipt rejection")
        } catch (failure: IllegalArgumentException) {
            assertTrue(failure.message.orEmpty().contains("staged receipt photo is missing"))
        }
        assertEquals(before, liveFiles())
        assertTrue(restoreStages().isEmpty())
    }

    @Test fun sharedReceiptIsArchivedOnceAndGetsOneFreshName() = runBlocking {
        savePhoto(first)
        val output = ByteArrayOutputStream()
        archive.writeEncrypted(output, backup(listOf(first, first)), "Shared photo backup password".toCharArray())
        archive.readEncrypted(ByteArrayInputStream(output.toByteArray()), "Shared photo backup password".toCharArray()).use { prepared ->
            assertEquals(1, prepared.receiptCount)
            val restored = archive.materialize(prepared)
            assertEquals(1, restored.refuels.map { it.receiptFile }.distinct().size)
            assertNotEquals(first, restored.refuels.first().receiptFile)
        }
    }

    @Test fun exportWithMissingReceiptFailsBeforeWritingAnyArchiveBytes() = runBlocking {
        val output = ByteArrayOutputStream()
        try {
            archive.writeEncrypted(output, backup(listOf(first)), "Missing receipt backup password".toCharArray())
            fail("Expected missing source photo rejection")
        } catch (failure: IllegalArgumentException) {
            assertTrue(failure.message.orEmpty().contains("receipt photo is missing"))
        }
        assertEquals(0, output.size())
    }

    @Test fun encryptedBackupRoundTripsWithOnlyCiphertextOnDiskAndClearsPasswords() = runBlocking {
        val image = jpeg()
        savePhoto(first, image)
        val original = backup(listOf(first)).copy(version = 3)
        val output = ByteArrayOutputStream()
        val exportPassword = "A private backup password 2026!".toCharArray()
        archive.writeEncrypted(output, original, exportPassword)
        assertTrue(exportPassword.all { it == '\u0000' })
        assertFalse(output.toByteArray().toString(Charsets.ISO_8859_1).contains("Petronas"))
        assertFalse(store.file(first).readBytes().contentEquals(image))
        val importPassword = "A private backup password 2026!".toCharArray()
        archive.readEncrypted(ByteArrayInputStream(output.toByteArray()), importPassword).use { prepared ->
            assertTrue(importPassword.all { it == '\u0000' })
            assertEquals(original, prepared.backup)
            assertEquals(1, prepared.receiptCount)
            assertFalse(prepared.photos.getValue(first).readBytes().contentEquals(image))
            assertFalse(File(prepared.stagingDirectory, "manifest.json").readText().contains("Petronas"))
            val restored = archive.materialize(prepared)
            val fresh = requireNotNull(restored.refuels.single().receiptFile)
            assertNotEquals(first, fresh)
            assertArrayEquals(image, store.readPhoto(fresh))
            assertFalse(store.file(fresh).readBytes().contentEquals(image))
        }
        assertTrue(restoreStages().isEmpty())
    }

    @Test fun encryptedBackupRejectsWrongPasswordTamperingAndTruncationBeforePreview() = runBlocking {
        savePhoto(first)
        val output = ByteArrayOutputStream()
        archive.writeEncrypted(output, backup(listOf(first)), "correct backup password".toCharArray())
        val valid = output.toByteArray()
        val before = liveFiles()
        val cases = listOf(
            valid to "wrong backup password",
            valid.clone().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() } to "correct backup password",
            valid.clone().also { it[20] = (it[20].toInt() xor 1).toByte() } to "correct backup password",
            valid.copyOf(valid.size - 1) to "correct backup password",
            valid.copyOf(valid.size - 100) to "correct backup password",
            (valid + byteArrayOf(1, 2, 3)) to "correct backup password"
        )
        for ((bytes, passwordText) in cases) {
            val password = passwordText.toCharArray()
            var rejected = false
            try {
                archive.readEncrypted(ByteArrayInputStream(bytes), password).close()
            } catch (_: Exception) { rejected = true }
            assertTrue("Invalid encrypted backup must be rejected before confirmation", rejected)
            assertTrue(password.all { it == '\u0000' })
            assertTrue(restoreStages().isEmpty())
            assertEquals(before, liveFiles())
        }
    }

    @Test fun encryptedBackupValidatesFinalTagAfterZipParserStops() = runBlocking {
        val output = ByteArrayOutputStream()
        val password = "A sufficiently long password".toCharArray()
        // Force the final authenticated segment to lie well beyond ZIP's central directory.
        BackupCrypto.encryptingStream(output, password).use { encrypted ->
            encrypted.write(zip(backup().copy(version = 3)))
            encrypted.write(ByteArray(200_000) { (it % 251).toByte() })
        }
        password.fill('\u0000')
        val tampered = output.toByteArray().also { it[it.lastIndex] = (it.last().toInt() xor 2).toByte() }
        var rejected = false
        try {
            archive.readEncrypted(ByteArrayInputStream(tampered), "A sufficiently long password".toCharArray()).close()
        } catch (_: Exception) { rejected = true }
        assertTrue("The final tag must be authenticated before offering a preview", rejected)
        assertTrue(restoreStages().isEmpty())
        assertTrue(liveFiles().isEmpty())
    }

    @Test fun encryptedExportRejectsShortPasswordAndClearsItOnFailure() = runBlocking {
        val output = ByteArrayOutputStream()
        val password = "short".toCharArray()
        try {
            archive.writeEncrypted(output, backup(), password)
            fail("Expected a suitable-password requirement")
        } catch (failure: IllegalArgumentException) {
            assertTrue(failure.message.orEmpty().contains("12"))
        }
        assertTrue(password.all { it == '\u0000' })
        assertEquals(0, output.size())
    }

    @Test fun genericExpenseReceiptsUseSameEncryptedBackupAndFreshNameRestore() = runBlocking {
        val image = jpeg()
        savePhoto(first, image)
        val original = backup().let { it.copy(version = 3,
            expenses = it.expenses + Expense("manual-expense", 1000, "food", 5000,
                description = "Lunch", receiptFile = first)) }
        val output = ByteArrayOutputStream()
        archive.writeEncrypted(output, original, "A generic expense password".toCharArray())
        archive.readEncrypted(ByteArrayInputStream(output.toByteArray()), "A generic expense password".toCharArray()).use { prepared ->
            assertEquals(original, prepared.backup)
            assertEquals(1, prepared.receiptCount)
            val restored = archive.materialize(prepared)
            val expense = restored.expenses.first { it.id == "manual-expense" }
            val fresh = requireNotNull(expense.receiptFile)
            assertNotEquals(first, fresh)
            assertArrayEquals(image, store.readPhoto(fresh))
            assertEquals(original.expenses.first { it.id == expense.id }.copy(receiptFile = fresh), expense)
            assertEquals(original.refuels, restored.refuels)
        }
    }

    @Test fun encryptedStagingRejectsLocalTamperingWithoutWritingPrivateCopies() = runBlocking {
        val prepared = archive.read(ByteArrayInputStream(zip(backup(listOf(first)), "receipts/$first" to jpeg())))
        val file = prepared.photos.getValue(first)
        file.writeBytes(file.readBytes().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() })
        var rejected = false
        try { archive.materialize(prepared) } catch (_: Exception) { rejected = true }
        assertTrue(rejected)
        assertTrue(liveFiles().isEmpty())
        assertTrue(restoreStages().isEmpty())
    }
    @Test fun encryptedArchiveIncludesAllFinanceListsAndHistoricalOccurrences() = runBlocking {
        val income = FinanceIncome("salary", 350000, 1000, "Salary", "Private")
        val budget = FinanceBudget("limit", "2026-10", 50000, "food")
        val rule = FinanceRecurring("rent", "Rent", 80000, "bills", "Monthly", "2026-10-01", "2026-10-01", false)
        val historical = Expense("historical", 1200, "food", 2000, recurringRuleId = "removed-rule", recurringDate = "2026-10-01")
        val original = backup().let { it.copy(version = 3, incomes = listOf(income), budgets = listOf(budget),
            recurring = listOf(rule), expenses = it.expenses + historical) }
        val output = ByteArrayOutputStream()
        archive.writeEncrypted(output, original, "Finance backup password".toCharArray())
        archive.readEncrypted(ByteArrayInputStream(output.toByteArray()), "Finance backup password".toCharArray()).use { prepared ->
            assertEquals(original, prepared.backup)
            assertEquals(original, archive.materialize(prepared))
        }
        assertTrue(restoreStages().isEmpty())
    }

    @Test fun encryptedReadCancellationClearsPasswordAndRemovesAllStaging() = runBlocking {
        val password = "Cancelled backup password".toCharArray()
        val source = object : InputStream() {
            override fun read(): Int = throw CancellationException("Cancelled backup import")
        }
        try {
            archive.readEncrypted(source, password).close()
            fail("Expected cancellation")
        } catch (_: CancellationException) { }
        assertTrue(password.all { it == '\u0000' })
        assertTrue(restoreStages().isEmpty())
        assertTrue(liveFiles().isEmpty())
    }

    @Test fun oversizedExportMetadataFailsWhileEncodingBeforeAnyCiphertextIsWritten() = runBlocking {
        val notes = "x".repeat(2000)
        val large = backup().let { source -> source.copy(version = 3,
            expenses = source.expenses + List(9_000) { Expense("large-$it", 100, "food", 1000, notes = notes) }) }
        val output = ByteArrayOutputStream()
        val password = "Oversized backup password".toCharArray()
        try {
            archive.writeEncrypted(output, large, password)
            fail("Expected a bounded metadata rejection")
        } catch (failure: IllegalArgumentException) {
            assertTrue(failure.message.orEmpty().contains("16 MB"))
        }
        assertEquals(0, output.size())
        assertTrue(password.all { it == '\u0000' })
        assertTrue(restoreStages().isEmpty())
    }
}
