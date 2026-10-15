package my.fuelledger.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import java.io.File
import java.util.UUID

/** Delegates camera capture to the device camera app; no app camera/storage permission is needed. */
@Composable
fun ReceiptCameraButton(enabled: Boolean, onPhoto: (Uri) -> Unit, onError: (String) -> Unit) {
    val context = LocalContext.current
    var capturePath by rememberSaveable { mutableStateOf<String?>(null) }
    val latestPhoto by rememberUpdatedState(onPhoto)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        capturePath?.let { path ->
            val file = File(path)
            if (success && file.isFile && file.length() > 0) latestPhoto(FileProvider.getUriForFile(context, "${context.packageName}.receipt-camera", file))
            else { file.delete(); if (success) onError("The camera did not return a photo. Try again or choose an image.") }
        }
        capturePath = null
    }
    OutlinedButton(onClick = {
        var capture: File? = null
        try {
            val directory = File(context.cacheDir, "receipt-capture").apply { check(isDirectory || mkdirs()) }
            val file = File(directory, "${UUID.randomUUID()}.jpg").also { capture = it; check(it.createNewFile()) }
            capturePath = file.absolutePath
            launcher.launch(FileProvider.getUriForFile(context, "${context.packageName}.receipt-camera", file))
        } catch (_: Exception) {
            capture?.delete(); capturePath = null
            onError("Camera capture is unavailable or was denied. Choose a receipt photo from your device instead.")
        }
    }, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Text("Take receipt photo") }
}
