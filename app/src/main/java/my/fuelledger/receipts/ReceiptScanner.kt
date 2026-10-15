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
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 1600) sample *= 2
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: throw IOException("Cannot read the receipt photo")
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
        parseReceipt(recognized)
    }

    private companion object { val DIRECT_EXECUTOR = Executor { it.run() } }
}
