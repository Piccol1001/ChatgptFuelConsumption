package my.fuelledger.receipts

import android.graphics.BitmapFactory
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.IOException
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/** Uses the bundled Latin model locally; scanning does not require network access. */
class ReceiptScanner {
    suspend fun scan(bytes: ByteArray): ReceiptSuggestions = withContext(Dispatchers.IO) {
        require(bytes.size in 1..8 * 1024 * 1024) { "The receipt photo is unavailable or too large" }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0 && bounds.outWidth.toLong() * bounds.outHeight <= 150_000_000L) {
            "The receipt is not a supported image"
        }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 4096 ||
            (bounds.outWidth / sample).toLong() * (bounds.outHeight / sample) > 4096L * 1600) sample *= 2
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: throw IOException("Cannot read the receipt photo")
        val qualityIssue = receiptImageQualityIssue(bitmap)
        val recognizer = try { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
        catch (error: Throwable) { bitmap.recycle(); throw error }
        val recognized = suspendCancellableCoroutine<String> { continuation ->
            try {
                // Keep image resources alive until ML Kit has finished, even if the UI cancels.
                recognizer.process(InputImage.fromBitmap(bitmap, 0)).addOnCompleteListener(DIRECT_EXECUTOR) { task ->
                    recognizer.close()
                    bitmap.recycle()
                    if (continuation.isActive) {
                        if (task.isSuccessful) continuation.resume(task.result.text)
                        else continuation.resumeWithException(task.exception ?: IOException("Receipt scanning failed"))
                    }
                }
            } catch (error: Throwable) {
                recognizer.close()
                bitmap.recycle()
                if (continuation.isActive) continuation.resumeWithException(error)
            }
        }
        parseReceipt(recognized).copy(imageQualityIssue = qualityIssue)
    }

    private companion object { val DIRECT_EXECUTOR = Executor { it.run() } }
}

/** A conservative local contrast/sharpness gate. Weak images still produce editable review suggestions. */
internal fun receiptImageQualityIssue(bitmap: android.graphics.Bitmap): String? {
    if (minOf(bitmap.width, bitmap.height) < 240) return "The image is too small for reliable automatic saving. Review or retake it."
    val step = maxOf(2, maxOf(bitmap.width, bitmap.height) / 400)
    fun grey(x: Int, y: Int): Int {
        val pixel = bitmap.getPixel(x, y)
        return (android.graphics.Color.red(pixel) * 30 + android.graphics.Color.green(pixel) * 59 + android.graphics.Color.blue(pixel) * 11) / 100
    }
    var low = 255; var high = 0; var sharpness = 0.0; var samples = 0
    for (y in 1 until bitmap.height - 1 step step) for (x in 1 until bitmap.width - 1 step step) {
        val centre = grey(x, y); low = minOf(low, centre); high = maxOf(high, centre)
        val laplacian = grey(x - 1, y) + grey(x + 1, y) + grey(x, y - 1) + grey(x, y + 1) - 4 * centre
        sharpness += laplacian.toDouble() * laplacian; samples++
    }
    return if (high - low < 50 || samples == 0 || sharpness / samples < 25) "The image may be blurred or poorly lit. Review the extracted values or retake it." else null
}
