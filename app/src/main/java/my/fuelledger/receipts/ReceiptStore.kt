package my.fuelledger.receipts

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import androidx.core.graphics.scale
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import my.fuelledger.security.LocalVault

/** Receipt originals are read only; retained copies are encrypted in application-private storage. */
class ReceiptStore(context: Context, private val vault: LocalVault = LocalVault(context)) {
    private val context = context.applicationContext
    private val directory = File(this.context.filesDir, "receipts")

    /** A logical UUID.jpg refers to encrypted bytes in UUID.jpg.enc, never a public image. */
    fun file(name: String): File {
        require(RECEIPT_NAME.matches(name)) { "Invalid receipt filename" }
        return File(directory, "$name.enc")
    }

    /** Decrypts into memory only. Call from an IO dispatcher; do not create plaintext disk copies. */
    fun readPhoto(name: String): ByteArray {
        val source = file(name)
        require(source.isFile) { "The receipt photo is no longer available" }
        val encrypted = source.inputStream().use { readBounded(it, MAX_PHOTO_BYTES + ENVELOPE_ALLOWANCE) }
        val plain = vault.decrypt(encrypted, "receipt:$name")
        require(plain.size in 4..MAX_PHOTO_BYTES) { "The receipt photo has an invalid size" }
        return plain
    }

    /** Imports, normalizes and encrypts a selected image without any plaintext temporary file. */
    suspend fun importPhoto(uri: Uri): String {
        var created: String? = null
        try {
            return withContext(Dispatchers.IO) {
                require(uri.scheme == "content" || uri.scheme == "file") { "Choose an image from your device" }
                val incoming = context.contentResolver.openInputStream(uri)?.use { readBounded(it, MAX_INPUT_BYTES) }
                    ?: throw IOException("Cannot open the selected image")
                require(incoming.isNotEmpty()) { "The selected image is empty" }
                val normalized = normalizeImage(incoming)
                saveRestored(normalized).also { created = it }
            }
        } catch (error: Throwable) {
            created?.let { file(it).delete() }
            throw error
        }
    }

    /** Creates a new encrypted copy; existing attachments are never overwritten. */
    suspend fun transformPhoto(name: String, rotate: Boolean, topPercent: Int, bottomPercent: Int): String = withContext(Dispatchers.IO) {
        require(topPercent in 0..45 && bottomPercent in 0..45 && topPercent + bottomPercent < 90) { "Crop percentages must keep at least 10% of the image." }
        val bytes = readPhoto(name)
        val original = try { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) } finally { bytes.fill(0) }
            ?: throw IOException("Cannot decode the receipt")
        var cropped: Bitmap? = null
        var rotated: Bitmap? = null
        try {
            val top = original.height * topPercent / 100
            val bottom = original.height * bottomPercent / 100
            val crop = Bitmap.createBitmap(original, 0, top, original.width, original.height - top - bottom).also { cropped = it }
            val result = if (rotate) Bitmap.createBitmap(crop, 0, 0, crop.width, crop.height, Matrix().apply { postRotate(90f) }, true).also { rotated = it } else crop
            val encoded = ByteArrayOutputStream().use { output ->
                check(result.compress(Bitmap.CompressFormat.JPEG, 95, output)); output.toByteArray()
            }
            try { saveRestored(encoded) } finally { encoded.fill(0) }
        } finally {
            rotated?.takeIf { it !== cropped && it !== original }?.recycle()
            cropped?.takeIf { it !== original }?.recycle()
            original.recycle()
        }
    }

    /** Validates an already normalized JPEG, encrypts it, and refuses to replace an existing photo. */
    suspend fun writePhoto(name: String, bytes: ByteArray) {
        var created: File? = null
        try {
            withContext(Dispatchers.IO) {
                val destination = file(name)
                validateNormalizedJpeg(bytes)
                val encrypted = vault.encrypt(bytes, "receipt:$name")
                currentCoroutineContext().ensureActive()
                storageMutex.withLock {
                    check(directory.isDirectory || directory.mkdirs()) { "Cannot create private receipt storage" }
                    check(!destination.exists()) { "A receipt photo already uses this name" }
                    // This staging file contains ciphertext exclusively; renaming publishes the complete envelope.
                    val staging = File.createTempFile("encrypted-receipt-", ".partial", directory)
                    try {
                        staging.outputStream().use { output -> output.write(encrypted); output.flush() }
                        currentCoroutineContext().ensureActive()
                        check(!destination.exists() && staging.renameTo(destination)) { "Cannot store the receipt photo" }
                        created = destination
                    } finally { staging.delete() }
                }
            }
        } catch (error: Throwable) {
            // Includes cancellation while dispatching the result back to the caller.
            created?.delete()
            throw error
        }
    }

    /** Fresh names also give restored photos fresh authenticated-encryption context and nonces. */
    suspend fun saveRestored(bytes: ByteArray): String {
        val name = "${UUID.randomUUID()}.jpg"
        writePhoto(name, bytes)
        return name
    }

    /** Upgrades previous private JPEG copies; system-picker originals and public albums are untouched. */
    suspend fun migrateLegacyPhotos(): Unit = withContext(Dispatchers.IO) {
        directory.listFiles().orEmpty().filter { RECEIPT_NAME.matches(it.name) && it.isFile }.forEach { legacy ->
            currentCoroutineContext().ensureActive()
            val plain = legacy.inputStream().use { readBounded(it, MAX_PHOTO_BYTES) }
            if (!file(legacy.name).exists()) writePhoto(legacy.name, plain)
            // Verify the retained copy before removing only the application's old private plaintext.
            check(readPhoto(legacy.name).contentEquals(plain)) { "Conflicting receipt copies require review" }
            check(legacy.delete()) { "Cannot remove the old unencrypted private receipt copy" }
        }
    }

    private fun normalizeImage(bytes: ByteArray): ByteArray {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Choose a supported image file" }
        require(bounds.outWidth.toLong() * bounds.outHeight <= MAX_SOURCE_PIXELS) { "Receipt image dimensions are too large" }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > MAX_EDGE * 2 ||
            (bounds.outWidth / sample).toLong() * (bounds.outHeight / sample) > 4096L * 1600) sample *= 2
        val original = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }) ?: throw IOException("Cannot decode the selected image")
        var oriented: Bitmap? = null
        var resized: Bitmap? = null
        try {
            val orientation = ExifInterface(ByteArrayInputStream(bytes)).getAttributeInt(
                ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
            )
            val matrix = orientationMatrix(orientation)
            val adjusted = if (matrix.isIdentity) original else Bitmap.createBitmap(
                original, 0, 0, original.width, original.height, matrix, true
            )
            oriented = adjusted
            val longest = maxOf(adjusted.width, adjusted.height)
            val targetEdge = if (adjusted.height >= adjusted.width * 2.5) MAX_EDGE else 1600
            val finalBitmap = if (longest <= targetEdge) adjusted else {
                val factor = targetEdge.toDouble() / longest
                adjusted.scale((adjusted.width * factor).toInt().coerceAtLeast(1),
                    (adjusted.height * factor).toInt().coerceAtLeast(1), true)
            }
            resized = finalBitmap
            return ByteArrayOutputStream().use { output ->
                check(finalBitmap.compress(Bitmap.CompressFormat.JPEG, 88, output)) { "Cannot save the receipt image" }
                output.toByteArray().also { require(it.size <= MAX_PHOTO_BYTES) { "The normalized receipt is too large" } }
            }
        } finally {
            listOfNotNull(resized, oriented, original).distinct().forEach { if (!it.isRecycled) it.recycle() }
        }
    }

    private fun validateNormalizedJpeg(bytes: ByteArray) {
        require(bytes.size in 4..MAX_PHOTO_BYTES) { "Receipt photos must be JPEG files no larger than 8 MB" }
        require(bytes[0] == 0xff.toByte() && bytes[1] == 0xd8.toByte() &&
            bytes[bytes.size - 2] == 0xff.toByte() && bytes.last() == 0xd9.toByte()) { "The receipt is not a complete JPEG image" }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        require(bounds.outMimeType == "image/jpeg" && bounds.outWidth in 1..MAX_EDGE && bounds.outHeight in 1..MAX_EDGE && bounds.outWidth.toLong() * bounds.outHeight <= 4096L * 1600) {
            "Receipt photos must be normalized JPEG images no larger than 4096 pixels"
        }
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        requireNotNull(decoded) { "The receipt photo cannot be decoded" }
        decoded.recycle()
    }

    private fun readBounded(input: InputStream, maximum: Int): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(32 * 1024)
        var total = 0L
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            if (count == 0) continue
            total += count
            require(total <= maximum) { "Receipt image exceeds its size limit" }
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    companion object {
        private const val MAX_INPUT_BYTES = 25 * 1024 * 1024
        private const val MAX_PHOTO_BYTES = 8 * 1024 * 1024
        private const val ENVELOPE_ALLOWANCE = 64 * 1024
        private const val MAX_SOURCE_PIXELS = 150_000_000L
        private const val MAX_EDGE = 4096
        private val RECEIPT_NAME = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.jpg")
        private val storageMutex = Mutex()

        private fun orientationMatrix(orientation: Int): Matrix = Matrix().apply {
            when (orientation) {
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> setScale(-1f, 1f)
                ExifInterface.ORIENTATION_ROTATE_180 -> setRotate(180f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> setScale(1f, -1f)
                ExifInterface.ORIENTATION_TRANSPOSE -> { setRotate(90f); postScale(-1f, 1f) }
                ExifInterface.ORIENTATION_ROTATE_90 -> setRotate(90f)
                ExifInterface.ORIENTATION_TRANSVERSE -> { setRotate(-90f); postScale(-1f, 1f) }
                ExifInterface.ORIENTATION_ROTATE_270 -> setRotate(-90f)
            }
        }
    }
}
