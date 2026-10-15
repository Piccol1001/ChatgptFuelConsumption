package my.fuelledger.receipts

import android.Manifest
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.io.RandomAccessFile
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class ReceiptStoreTest {
    private lateinit var context: Context
    private lateinit var store: ReceiptStore
    private val sources = mutableListOf<File>()

    @Before fun setup() {
        context = ApplicationProvider.getApplicationContext()
        File(context.filesDir, "receipts").deleteRecursively()
        store = ReceiptStore(context)
    }

    @After fun cleanup() {
        sources.forEach(File::delete)
        File(context.filesDir, "receipts").deleteRecursively()
    }

    @Test fun filenamesMustBeLowercaseCanonicalUuidJpegs() {
        val valid = "12345678-1234-1234-1234-123456abcdef.jpg"
        assertEquals(File(context.filesDir, "receipts/$valid"), store.file(valid))
        listOf("../$valid", "/$valid", valid.uppercase(), valid.replace(".jpg", ".png"),
            "12345678123412341234123456abcdef.jpg", "1-1-1-1-1.jpg", "$valid/../../secret", "..", "").forEach {
            try { store.file(it); fail("Accepted unsafe filename: $it") } catch (_: IllegalArgumentException) { }
        }
    }

    @Test fun importCreatesBoundedPrivateJpegAndPreservesOriginal() = runBlocking {
        val source = image(3200, 800, Bitmap.CompressFormat.PNG)
        val original = source.readBytes()
        val name = store.importPhoto(Uri.fromFile(source))
        assertTrue(Regex("[0-9a-f-]{36}\\.jpg").matches(name))
        val saved = store.file(name)
        assertTrue(saved.isFile)
        assertArrayEquals(original, source.readBytes())
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(saved.absolutePath, bounds)
        assertEquals(1600, bounds.outWidth)
        assertEquals(400, bounds.outHeight)
        assertEquals("image/jpeg", bounds.outMimeType)
        assertEquals(listOf(name), saved.parentFile!!.list()!!.toList())
    }

    @Test fun importAppliesExifRotationBeforeRemovingTheOriginalMetadata() = runBlocking {
        val source = image(320, 120)
        ExifInterface(source).apply {
            setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString())
            saveAttributes()
        }
        val saved = store.file(store.importPhoto(Uri.fromFile(source)))
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(saved.absolutePath, bounds)
        assertEquals(120, bounds.outWidth)
        assertEquals(320, bounds.outHeight)
        assertNotEquals(ExifInterface.ORIENTATION_ROTATE_90,
            ExifInterface(saved).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL))
        assertEquals(ExifInterface.ORIENTATION_ROTATE_90,
            ExifInterface(source).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL))
    }

    @Test fun invalidAndOversizeInputsLeaveNoPrivateReceiptOrPartialFiles() = runBlocking {
        val bad = File.createTempFile("bad-receipt", ".txt", context.cacheDir).also { sources += it; it.writeText("not an image") }
        val empty = File.createTempFile("empty-receipt", ".jpg", context.cacheDir).also { sources += it }
        val huge = File.createTempFile("huge-receipt", ".jpg", context.cacheDir).also {
            sources += it
            RandomAccessFile(it, "rw").use { file -> file.setLength(25L * 1024 * 1024 + 1) }
        }
        listOf(bad, empty, huge).forEach { source ->
            try { store.importPhoto(Uri.fromFile(source)); fail("Accepted invalid image") }
            catch (_: IllegalArgumentException) { }
            assertTrue(source.exists())
        }
        assertTrue(File(context.filesDir, "receipts").listFiles().orEmpty().isEmpty())
        assertFalse(context.cacheDir.listFiles().orEmpty().any { it.name.startsWith("receipt-import-") })
    }

    @Test fun repeatedImportsCreateIndependentCopies() = runBlocking {
        val source = image(100, 200)
        val first = store.importPhoto(Uri.fromFile(source))
        val second = store.importPhoto(Uri.fromFile(source))
        assertNotEquals(first, second)
        assertTrue(store.file(first).exists())
        assertTrue(store.file(second).exists())
    }

    @Test @Config(sdk = [28]) fun legacyGalleryExportRequiresPermissionBeforeWriting() = runBlocking {
        val name = store.importPhoto(Uri.fromFile(image(100, 200)))
        Shadows.shadowOf(context as android.app.Application).denyPermissions(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        try { store.saveToGallery(name); fail("Expected storage permission request") }
        catch (_: SecurityException) { }
        assertTrue(store.file(name).exists())
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
