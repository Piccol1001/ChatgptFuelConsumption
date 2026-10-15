package my.fuelledger.data

import android.content.Context
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.encodeToStream
import my.fuelledger.receipts.ReceiptStore
import my.fuelledger.security.LocalVault
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.File
import java.io.FilterInputStream
import java.io.FilterOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.io.PushbackInputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * A validated restore preview. Photos remain in the private cache until the user confirms.
 * Close it when the preview is dismissed or after the repository restore completes.
 */
class PreparedRestore internal constructor(
    val backup: Backup,
    internal val stagingDirectory: File,
    internal val photos: Map<String, File>
) : Closeable {
    val receiptCount: Int get() = photos.size
    @Volatile private var closed = false

    internal fun requireOpen() = check(!closed) { "This backup preview has been closed. Select the backup again." }

    override fun close() {
        if (!closed) {
            closed = true
            stagingDirectory.deleteRecursively()
        }
    }
}

/** Complete, bounded backups including receipt photos; legacy JSON backups remain readable. */
class ReceiptBackupArchive(
    context: Context,
    private val receipts: ReceiptStore,
    private val vault: LocalVault = LocalVault(context)
) {
    private val cacheDirectory = context.applicationContext.cacheDir

    /** Production exports always use this password-encrypted, authenticated streaming format. */
    suspend fun writeEncrypted(output: OutputStream, backup: Backup, password: CharArray) {
        try {
            withContext(Dispatchers.IO) {
                require(password.size in 12..1024) { "Use a backup password containing 12–1024 characters." }
                val prepared = prepareExport(backup.copy(version = maxOf(3, backup.version)))
                try {
                    BackupCrypto.encryptingStream(output.buffered(), password).use { encrypted ->
                        writeArchive(encrypted, prepared)
                    }
                } finally {
                    prepared.manifest.fill(0)
                }
            }
        } finally {
            password.fill('\u0000')
        }
    }

    /**
     * Authentication is checked through EOF before returning a confirmation preview. Password
     * characters are consumed and cleared, including on invalid data or cancellation.
     */
    suspend fun readEncrypted(input: InputStream, password: CharArray): PreparedRestore {
        var prepared: PreparedRestore? = null
        try {
            return withContext(Dispatchers.IO) {
                BackupCrypto.decryptingStream(input.buffered(), password).use { decrypted ->
                    // ZIP parsing can stop at its central directory, before the AEAD's final tag.
                    // Keep the decryptor open, then drain every remaining authenticated segment.
                    val bounded = BoundedInputStream(decrypted, MAX_EXPANDED_BYTES + 32L * 1024 * 1024)
                    val result = read(NonClosingInputStream(bounded))
                    prepared = result
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        if (bounded.read(buffer) < 0) break
                    }
                    currentCoroutineContext().ensureActive()
                    result
                }
            }
        } catch (failure: Throwable) {
            prepared?.close()
            throw failure
        } finally {
            password.fill('\u0000')
        }
    }

    private data class PreparedExport(val manifest: ByteArray, val photoNames: Set<String>)

    @OptIn(ExperimentalSerializationApi::class)
    private suspend fun prepareExport(backup: Backup): PreparedExport {
        backup.validate()
        // Enforce the limit while encoding rather than allocating an oversized JSON String.
        val manifest = LimitedByteArrayOutputStream(MAX_MANIFEST_BYTES).also {
            backupJson.encodeToStream(backup, it)
        }.toByteArray()
        val photoNames = backup.receiptNames()
        require(photoNames.size + 1 <= MAX_ENTRIES) { "Backup contains too many receipt photos." }
        var expandedSize = manifest.size.toLong()
        // Preflight without retaining all photos in RAM and before writing any output.
        photoNames.forEach { name ->
            currentCoroutineContext().ensureActive()
            require(receipts.file(name).isFile) { "A receipt photo is missing. Reattach it before creating a backup." }
            val bytes = receipts.readPhoto(name)
            try {
                validateJpeg(bytes)
                expandedSize += bytes.size
                require(expandedSize <= MAX_EXPANDED_BYTES) { "Backup exceeds the 256 MB expanded size limit." }
            } finally {
                bytes.fill(0)
            }
        }
        require(expandedSize <= MAX_EXPANDED_BYTES) {
            "Backup exceeds the 256 MB expanded size limit."
        }
        return PreparedExport(manifest, photoNames)
    }

    private suspend fun writeArchive(output: OutputStream, prepared: PreparedExport) {
        val manifest = prepared.manifest
        ZipOutputStream(NonClosingOutputStream(output).buffered()).use { zip ->
            zip.putNextEntry(ZipEntry(MANIFEST))
            zip.write(manifest)
            zip.closeEntry()
            var total = manifest.size.toLong()
            prepared.photoNames.forEach { name ->
                currentCoroutineContext().ensureActive()
                zip.putNextEntry(ZipEntry("receipts/$name"))
                val bytes = receipts.readPhoto(name)
                try {
                    validateJpeg(bytes)
                    ByteArrayInputStream(bytes).use { source ->
                        copyBounded(source, zip, MAX_PHOTO_BYTES) { count ->
                            total += count
                            require(total <= MAX_EXPANDED_BYTES) { "Backup exceeds the 256 MB expanded size limit." }
                        }
                    }
                } finally {
                    bytes.fill(0)
                }
                zip.closeEntry()
            }
        }
    }

    /** Fully validates records and images without changing the database or private receipt files. */
    suspend fun read(input: InputStream): PreparedRestore {
        var prepared: PreparedRestore? = null
        var staging: File? = null
        try {
            return withContext(Dispatchers.IO) {
                val directory = File(cacheDirectory, "receipt-restore-${UUID.randomUUID()}")
                check(directory.mkdirs()) { "Could not prepare backup preview storage." }
                staging = directory
                PushbackInputStream(input.buffered(), 4).use { source ->
                    val prefix = ByteArray(4)
                    var size = 0
                    while (size < prefix.size) {
                        val count = source.read(prefix, size, prefix.size - size)
                        if (count < 0) break
                        if (count > 0) size += count
                    }
                    source.unread(prefix, 0, size)
                    val isZip = size == 4 && prefix[0] == 0x50.toByte() && prefix[1] == 0x4b.toByte() &&
                        ((prefix[2] == 3.toByte() && prefix[3] == 4.toByte()) ||
                            (prefix[2] == 5.toByte() && prefix[3] == 6.toByte()))
                    val result = if (isZip) readZip(source, directory) else readJson(source, directory)
                    currentCoroutineContext().ensureActive()
                    prepared = result
                    result
                }
            }
        } catch (failure: Throwable) {
            // Also handles cancellation during the dispatch back to the calling context.
            prepared?.close() ?: staging?.deleteRecursively()
            throw failure
        }
    }

    /**
     * Call only after restore confirmation. New names prevent replacement of existing photos,
     * including when the subsequent database restore fails. The caller still owns the preview.
     */
    suspend fun materialize(prepared: PreparedRestore): Backup {
        val created = mutableListOf<File>()
        try {
            return withContext(Dispatchers.IO) {
                prepared.requireOpen()
                prepared.backup.validate()
                val names = mutableMapOf<String, String>()
                prepared.photos.forEach { (oldName, source) ->
                    currentCoroutineContext().ensureActive()
                    require(source.isFile) { "A staged receipt photo is missing. Select the backup again." }
                    val bytes = readStage(prepared.stagingDirectory, source)
                    try {
                        validateJpeg(bytes)
                        val newName = receipts.saveRestored(bytes)
                        created += receipts.file(newName)
                        names[oldName] = newName
                    } finally {
                        bytes.fill(0)
                    }
                }
                prepared.backup.copy(
                    refuels = prepared.backup.refuels.map { refuel ->
                        refuel.copy(receiptFile = refuel.receiptFile?.let { requireNotNull(names[it]) })
                    },
                    expenses = prepared.backup.expenses.map { expense ->
                        expense.copy(receiptFile = expense.receiptFile?.let { requireNotNull(names[it]) })
                    }
                ).also { it.validate() }
            }
        } catch (failure: Throwable) {
            created.forEach { it.delete() }
            prepared.close()
            throw failure
        }
    }

    private suspend fun readJson(input: InputStream, staging: File): PreparedRestore {
        val manifest = File(staging, MANIFEST)
        val bytes = readBounded(input, MAX_MANIFEST_BYTES.toLong())
        try {
            writeStage(staging, manifest, bytes)
        } finally {
            bytes.fill(0)
        }
        val backup = parseManifest(staging, manifest)
        require(backup.receiptNames().isEmpty()) {
            "This JSON backup references receipt photos that are missing. Select the complete ZIP backup instead."
        }
        return PreparedRestore(backup, staging, emptyMap())
    }

    private suspend fun readZip(input: InputStream, staging: File): PreparedRestore {
        var total = 0L
        val seen = mutableSetOf<String>()
        val photos = linkedMapOf<String, File>()
        var manifest: File? = null
        ZipInputStream(NonClosingInputStream(input)).use { zip ->
            while (true) {
                currentCoroutineContext().ensureActive()
                val entry = zip.nextEntry ?: break
                require(seen.add(entry.name)) { "Backup contains a duplicate archive entry." }
                require(seen.size <= MAX_ENTRIES) { "Backup contains too many archive entries." }
                require(!entry.isDirectory) { "Backup contains an unsupported directory entry." }
                val isManifest = entry.name == MANIFEST
                val name = if (isManifest) MANIFEST else {
                    require(PHOTO_ENTRY.matches(entry.name)) { "Backup contains an unsafe or unsupported archive path." }
                    entry.name.removePrefix("receipts/")
                }
                val limit = if (isManifest) MAX_MANIFEST_BYTES.toLong() else MAX_PHOTO_BYTES
                require(entry.size <= limit) { "A backup entry exceeds its size limit." }
                val file = File(staging, name)
                val bytes = readBounded(zip, limit) { count ->
                        total += count
                        require(total <= MAX_EXPANDED_BYTES) { "Backup exceeds the 256 MB expanded size limit." }
                }
                try {
                    writeStage(staging, file, bytes)
                } finally {
                    bytes.fill(0)
                }
                if (isManifest) manifest = file else photos[name] = file
                zip.closeEntry()
            }
        }
        val backup = parseManifest(staging, requireNotNull(manifest) { "Backup is missing manifest.json." })
        require(backup.version in 2..4) { "A ZIP backup must use format version 2, 3 or 4." }
        val references = backup.receiptNames()
        require(photos.keys.containsAll(references)) { "Backup is missing one or more receipt photos." }
        require(references.containsAll(photos.keys)) { "Backup contains unreferenced receipt photos." }
        photos.values.forEach {
            currentCoroutineContext().ensureActive()
            val bytes = readStage(staging, it)
            try { validateJpeg(bytes) } finally { bytes.fill(0) }
        }
        return PreparedRestore(backup, staging, photos)
    }

    private fun parseManifest(staging: File, file: File): Backup {
        val bytes = readStage(staging, file)
        val text = try { Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes)).toString().removePrefix("\uFEFF")
        } finally { bytes.fill(0) }
        return backupJson.decodeFromString<Backup>(text).also { it.validate() }
    }

    private fun Backup.receiptNames(): Set<String> =
        (refuels.mapNotNull { it.receiptFile } + expenses.mapNotNull { it.receiptFile }).toSet()

    private fun stagePurpose(staging: File, file: File) = "backup-stage:${staging.name}:${file.name}"

    private fun writeStage(staging: File, file: File, bytes: ByteArray) {
        file.writeBytes(vault.encrypt(bytes, stagePurpose(staging, file)))
    }

    private fun readStage(staging: File, file: File): ByteArray =
        vault.decrypt(file.readBytes(), stagePurpose(staging, file))

    private suspend fun readBounded(input: InputStream, maximum: Long, account: (Long) -> Unit = {}): ByteArray =
        ByteArrayOutputStream().also { copyBounded(input, it, maximum, account) }.toByteArray()

    private suspend fun copyBounded(
        input: InputStream,
        output: OutputStream,
        maximum: Long,
        account: (Long) -> Unit = {}
    ) {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var count = 0L
        while (true) {
            currentCoroutineContext().ensureActive()
            val size = input.read(buffer)
            if (size < 0) break
            if (size == 0) continue
            count += size
            require(count <= maximum) { "A backup entry exceeds its size limit." }
            account(size.toLong())
            output.write(buffer, 0, size)
        }
    }

    private fun validateJpeg(bytes: ByteArray) {
        require(bytes.size.toLong() in 4..MAX_PHOTO_BYTES) { "Receipt photos must be JPEG files no larger than 8 MB." }
        require(bytes[0] == 0xff.toByte() && bytes[1] == 0xd8.toByte()) { "A receipt photo is not a JPEG image." }
        require(bytes[bytes.size - 2] == 0xff.toByte() && bytes.last() == 0xd9.toByte()) {
            "A receipt photo is incomplete or is not a normalized JPEG image."
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        require(bounds.outMimeType == "image/jpeg" && bounds.outWidth in 1..MAX_PHOTO_SIDE &&
            bounds.outHeight in 1..MAX_PHOTO_SIDE && bounds.outWidth.toLong() * bounds.outHeight <= MAX_PHOTO_PIXELS) {
            "A receipt photo has an unsupported format or dimensions."
        }
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        require(bitmap != null) { "A receipt photo could not be decoded." }
        bitmap.recycle()
    }

    private class NonClosingInputStream(input: InputStream) : FilterInputStream(input) {
        override fun close() = Unit
    }

    private class NonClosingOutputStream(output: OutputStream) : FilterOutputStream(output) {
        override fun close() = flush()
    }

    private class LimitedByteArrayOutputStream(private val maximum: Int) : ByteArrayOutputStream() {
        override fun write(value: Int) {
            require(count < maximum) { "Backup metadata exceeds 16 MB." }
            super.write(value)
        }
        override fun write(bytes: ByteArray, offset: Int, length: Int) {
            require(length <= maximum - count) { "Backup metadata exceeds 16 MB." }
            super.write(bytes, offset, length)
        }
    }

    private class BoundedInputStream(input: InputStream, private val limit: Long) : FilterInputStream(input) {
        private var count = 0L
        override fun read(): Int = `in`.read().also { if (it >= 0) account(1) }
        override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
            `in`.read(buffer, offset, length).also { if (it > 0) account(it) }
        private fun account(bytes: Int) {
            count += bytes
            require(count <= limit) { "Encrypted backup payload exceeds its size limit." }
        }
    }

    private companion object {
        const val MANIFEST = "manifest.json"
        const val MAX_MANIFEST_BYTES = 16 * 1024 * 1024
        const val MAX_PHOTO_BYTES = 8L * 1024 * 1024
        const val MAX_EXPANDED_BYTES = 256L * 1024 * 1024
        const val MAX_ENTRIES = 100_001
        // Matches ReceiptStore normalization and bounds full decode to roughly 10 MB ARGB.
        const val MAX_PHOTO_SIDE = 4096
        const val MAX_PHOTO_PIXELS = 4096L * 1600
        val PHOTO_ENTRY = Regex("receipts/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.jpg")
    }
}
