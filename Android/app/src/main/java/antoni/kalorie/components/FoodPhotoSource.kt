package antoni.kalorie.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import antoni.kalorie.R
import antoni.kalorie.core.foodphoto.FoodPhotoProcessing
import antoni.kalorie.core.utils.Constants
import antoni.kalorie.core.utils.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

// MARK: - Body

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoodPhotoSource(
    isPresented: Boolean,
    onDismiss: () -> Unit,
    onProcessingChange: (Boolean) -> Unit = {},
    showAction: (() -> Unit)? = null,
    onProcessed: suspend (ByteArray) -> Unit,
) {
    // MARK: - Properties

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentOnProcessed = rememberUpdatedState(onProcessed)
    val currentOnProcessingChange = rememberUpdatedState(onProcessingChange)
    var isCameraDeniedVisible by remember { mutableStateOf(false) }
    var isProcessingFailedVisible by remember { mutableStateOf(false) }
    var pendingCameraFile by rememberSaveable { mutableStateOf<String?>(null) }

    fun apply(readBytes: () -> ByteArray) {
        scope.launch {
            currentOnProcessingChange.value(true)
            try {
                val processed = withContext(Dispatchers.Default) { FoodPhotoProcessing.process(readBytes()) }
                currentOnProcessed.value(processed)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.error(error, Constants.LogCategory.STORAGE)
                isProcessingFailedVisible = true
            } finally {
                currentOnProcessingChange.value(false)
            }
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { isSaved ->
        val path = pendingCameraFile
        pendingCameraFile = null
        if (isSaved && path != null) {
            apply {
                val file = File(path)
                try {
                    file.readBytes()
                } finally {
                    file.delete()
                }
            }
        } else if (path != null) {
            File(path).delete()
        }
    }
    val libraryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            apply { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: throw IllegalStateException("Unreadable photo") }
        }
    }
    val scannerAccess = rememberScannerAccess(
        onGranted = {
            val file = newCameraFile(context)
            pendingCameraFile = file.absolutePath
            cameraLauncher.launch(FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file))
        },
        onDenied = { isCameraDeniedVisible = true },
    )

    // MARK: - Sheet

    if (isPresented) {
        ModalBottomSheet(onDismissRequest = onDismiss) {
            Column(modifier = Modifier.padding(bottom = 16.dp)) {
                if (showAction != null) {
                    SheetAction(stringResource(R.string.foodPhoto_action_show)) {
                        onDismiss()
                        showAction()
                    }
                }
                if (context.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_CAMERA_ANY)) {
                    SheetAction(stringResource(R.string.foodPhoto_action_take)) {
                        onDismiss()
                        scannerAccess.open()
                    }
                }
                SheetAction(stringResource(R.string.foodPhoto_action_choose)) {
                    onDismiss()
                    libraryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
                SheetAction(stringResource(R.string.common_button_cancel), onDismiss)
            }
        }
    }
    if (isCameraDeniedVisible) {
        AlertDialog(
            onDismissRequest = { isCameraDeniedVisible = false },
            text = { Text(stringResource(R.string.addFood_camera_permissionAlert)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        isCameraDeniedVisible = false
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
                        )
                    },
                ) { Text(stringResource(R.string.addFood_button_openSettings)) }
            },
            dismissButton = {
                TextButton(onClick = { isCameraDeniedVisible = false }) { Text(stringResource(R.string.common_button_cancel)) }
            },
        )
    }
    if (isProcessingFailedVisible) {
        AlertDialog(
            onDismissRequest = { isProcessingFailedVisible = false },
            title = { Text(stringResource(R.string.common_error_unknown)) },
            confirmButton = {
                TextButton(onClick = { isProcessingFailedVisible = false }) { Text(stringResource(R.string.common_ok)) }
            },
        )
    }
}

@Composable
private fun SheetAction(text: String, onClick: () -> Unit) {
    ListItem(headlineContent = { Text(text) }, modifier = Modifier.clickable(onClick = onClick))
}

private fun newCameraFile(context: Context): File {
    val directory = File(context.cacheDir, Constants.CacheFolder.PHOTOS).apply { mkdirs() }
    return File(directory, "${UUID.randomUUID()}.jpg")
}
