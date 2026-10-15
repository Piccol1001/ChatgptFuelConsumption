package my.fuelledger.receipts

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import androidx.test.core.app.ApplicationProvider
import java.io.ByteArrayInputStream
import java.io.File
import java.io.RandomAccessFile
import java.security.GeneralSecurityException
import kotlinx.coroutines.runBlocking
import my.fuelledger.security.LocalVault
import my.fuelledger.testing.TestLedgerApplication
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = TestLedgerApplication::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReceiptStoreTest {
    private lateinit var context: Context
    private lateinit var vault: LocalVault
    private lateinit var store: ReceiptStore
    private val sources = mutableListOf<File>()

    @Before fun setup() {
        context = ApplicationProvider.getApplicationContext()
        vault = (context as TestLedgerApplication).vault
        File(context.filesDir, "receipts").deleteRecursively()
        store = ReceiptStore(context, vault)
    }

    @After fun cleanup() {
        sources.forEach(File::delete)
        File(context.filesDir, "receipts").deleteRecursively()
    }

    @Test fun filenamesMustBeLowercaseCanonicalUuidJpegs() {
        val valid = "12345678-1234-1234-1234-123456abcdef.jpg"
        assertEquals(File(context.filesDir, "receipts/$valid.enc"), store.file(valid))
        listOf("../$valid", "/$valid", valid.uppercase(), valid.replace(".jpg", ".png"),
            "12345678123412341234123456abcdef.jpg", "1-1-1-1-1.jpg", "$valid/../../secret", "..", "").forEach {
            try { store.file(it); fail("Accepted unsafe filename: $it") } catch (_: IllegalArgumentException) { }
        }
    }

    @Test fun importCreatesBoundedEncryptedPrivateJpegAndPreservesOriginal() = runBlocking {
        val source = image(3200, 800, Bitmap.CompressFormat.PNG)
        val original = source.readBytes()
        val cacheBefore = context.cacheDir.list().orEmpty().toSet()
        val name = store.importPhoto(Uri.fromFile(source))
        assertTrue(Regex("[0-9a-f-]{36}\\.jpg").matches(name))
        val saved = store.file(name)
        assertTrue(saved.isFile)
        assertArrayEquals(original, source.readBytes())
        val decrypted = store.readPhoto(name)
        val bounds = bounds(decrypted)
        assertEquals(1600, bounds.outWidth)
        assertEquals(400, bounds.outHeight)
        assertEquals("image/jpeg", bounds.outMimeType)
        assertFalse(saved.readBytes().contentEquals(decrypted))
        assertFalse(saved.readBytes().take(2) == listOf(0xff.toByte(), 0xd8.toByte()))
        assertEquals(listOf("$name.enc"), saved.parentFile!!.list()!!.toList())
        assertEquals(cacheBefore, context.cacheDir.list().orEmpty().toSet())
    }

    @Test fun importAppliesExifRotationWithoutPersistingPlaintextMetadata() = runBlocking {
        val source = image(320, 120)
        ExifInterface(source).apply {
            setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString())
            setAttribute(ExifInterface.TAG_USER_COMMENT, "Sensitive receipt annotation")
            saveAttributes()
        }
        val name = store.importPhoto(Uri.fromFile(source))
        val plain = store.readPhoto(name)
        assertEquals(120, bounds(plain).outWidth)
        assertEquals(320, bounds(plain).outHeight)
        val savedExif = ExifInterface(ByteArrayInputStream(plain))
        assertNotEquals(ExifInterface.ORIENTATION_ROTATE_90,
            savedExif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL))
        assertNull(savedExif.getAttribute(ExifInterface.TAG_USER_COMMENT))
        assertEquals(ExifInterface.ORIENTATION_ROTATE_90,
            ExifInterface(source).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL))
    }

    @Test fun invalidAndOversizeInputsLeaveNoPrivateReceiptOrTemporaryPlaintextFiles() = runBlocking {
        val bad = File.createTempFile("bad-receipt", ".txt", context.cacheDir).also { sources += it; it.writeText("not an image") }
        val empty = File.createTempFile("empty-receipt", ".jpg", context.cacheDir).also { sources += it }
        val huge = File.createTempFile("huge-receipt", ".jpg", context.cacheDir).also {
            sources += it
            RandomAccessFile(it, "rw").use { file -> file.setLength(25L * 1024 * 1024 + 1) }
        }
        val cacheBefore = context.cacheDir.list().orEmpty().toSet()
        listOf(bad, empty, huge).forEach { source ->
            try { store.importPhoto(Uri.fromFile(source)); fail("Accepted invalid image") }
            catch (_: IllegalArgumentException) { }
            assertTrue(source.exists())
        }
        assertTrue(File(context.filesDir, "receipts").listFiles().orEmpty().isEmpty())
        assertEquals(cacheBefore, context.cacheDir.list().orEmpty().toSet())
    }

    @Test fun repeatedImportsCreateIndependentEncryptedCopiesReadableAfterReopening() = runBlocking {
        val source = image(100, 200)
        val first = store.importPhoto(Uri.fromFile(source))
        val second = store.importPhoto(Uri.fromFile(source))
        assertNotEquals(first, second)
        assertFalse(store.file(first).readBytes().contentEquals(store.file(second).readBytes()))
        assertArrayEquals(store.readPhoto(first), ReceiptStore(context, vault).readPhoto(first))
        assertArrayEquals(store.readPhoto(first), store.readPhoto(second))
    }

    @Test fun authenticatedReceiptCannotBeTamperedWithOrMovedToAnotherReference() = runBlocking {
        val first = store.importPhoto(Uri.fromFile(image(100, 200)))
        val second = "23456789-1234-1234-1234-123456abcdef.jpg"
        store.file(first).copyTo(store.file(second))
        expectAuthenticationFailure { store.readPhoto(second) }
        val modified = store.file(first).readBytes().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }
        store.file(first).writeBytes(modified)
        expectAuthenticationFailure { store.readPhoto(first) }
    }

    @Test fun restoreWritesFreshEncryptedReferencesAndRefusesOverwriteOrInvalidJpeg() = runBlocking {
        val bytes = image(100, 200).readBytes()
        val first = store.saveRestored(bytes)
        val encrypted = store.file(first).readBytes()
        try { store.writePhoto(first, bytes); fail("Existing receipt was overwritten") } catch (_: IllegalStateException) { }
        assertArrayEquals(encrypted, store.file(first).readBytes())
        assertArrayEquals(bytes, store.readPhoto(first))
        val before = store.file(first).parentFile!!.list().orEmpty().toSet()
        try { store.saveRestored(byteArrayOf(0xff.toByte(), 0xd8.toByte(), 0xff.toByte(), 0xd9.toByte())); fail("Accepted invalid JPEG") }
        catch (_: IllegalArgumentException) { }
        assertEquals(before, store.file(first).parentFile!!.list().orEmpty().toSet())
    }

    @Test fun legacyPrivatePhotosMigrateWithoutChangingSelectedOriginals() = runBlocking {
        val source = image(100, 200)
        val original = source.readBytes()
        val name = "12345678-1234-1234-1234-123456abcdef.jpg"
        val legacy = File(context.filesDir, "receipts/$name")
        legacy.parentFile!!.mkdirs()
        legacy.writeBytes(original)
        store.migrateLegacyPhotos()
        assertFalse(legacy.exists())
        assertTrue(store.file(name).exists())
        assertArrayEquals(original, store.readPhoto(name))
        assertArrayEquals(original, source.readBytes())
        store.migrateLegacyPhotos()
        assertArrayEquals(original, store.readPhoto(name))
    }

    private fun expectAuthenticationFailure(read: () -> ByteArray) {
        try { read(); fail("Expected authenticated encryption to reject changed receipt") }
        catch (_: GeneralSecurityException) { }
    }

    private fun bounds(bytes: ByteArray) = BitmapFactory.Options().apply {
        inJustDecodeBounds = true
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, this)
    }

    private fun image(width: Int, height: Int, format: Bitmap.CompressFormat = Bitmap.CompressFormat.JPEG): File {
        val file = File.createTempFile("receipt-source-", ".image", context.cacheDir).also { sources += it }
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.WHITE)
        file.outputStream().use { assertTrue(bitmap.compress(format, 90, it)) }
        bitmap.recycle()
        return file
    }
}
