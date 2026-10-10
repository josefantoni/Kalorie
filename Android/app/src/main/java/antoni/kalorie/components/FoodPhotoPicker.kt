package antoni.kalorie.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import antoni.kalorie.R
import antoni.kalorie.core.foodphoto.FoodPhotoProcessing
import antoni.kalorie.core.models.FoodItemFormPhoto
import antoni.kalorie.core.utils.Constants
import antoni.kalorie.core.utils.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

private val PHOTO_DIAMETER = 120.dp
private val PHOTO_BADGE_DIAMETER = 36.dp

// MARK: - Body

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoodPhotoPicker(
    photo: FoodItemFormPhoto,
    onPhotoChange: (FoodItemFormPhoto) -> Unit,
    modifier: Modifier = Modifier,
    isMissing: Boolean = false,
    onChanged: () -> Unit = {},
) {
    // MARK: - Properties

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentOnPhotoChange = rememberUpdatedState(onPhotoChange)
    val currentOnChanged = rememberUpdatedState(onChanged)
    var isSheetVisible by remember { mutableStateOf(false) }
    var isViewerVisible by remember { mutableStateOf(false) }
    var isCameraDeniedVisible by remember { mutableStateOf(false) }
    var isProcessingFailedVisible by remember { mutableStateOf(false) }
    var pendingCameraFile by rememberSaveable { mutableStateOf<String?>(null) }

    fun apply(readBytes: () -> ByteArray) {
        scope.launch {
            try {
                val processed = withContext(Dispatchers.Default) { FoodPhotoProcessing.process(readBytes()) }
                currentOnPhotoChange.value(FoodItemFormPhoto.Local(processed))
                currentOnChanged.value()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.error(error, Constants.LogCategory.STORAGE)
                isProcessingFailedVisible = true
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
    val title = stringResource(R.string.foodPhoto_title)
    val outlineColor = if (isMissing) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary
    val actionDescription = stringResource(if (photo == FoodItemFormPhoto.None) R.string.foodPhoto_action_take else R.string.foodPhoto_accessibility_retake)

    // MARK: - Body

    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(modifier = Modifier.size(PHOTO_DIAMETER).semantics { contentDescription = "$title, $actionDescription" }) {
            Box(modifier = Modifier.size(PHOTO_DIAMETER).clip(CircleShape).clickable { isSheetVisible = true }) {
                if (photo == FoodItemFormPhoto.None) {
                    Box(
                        modifier = Modifier.size(PHOTO_DIAMETER).drawBehind {
                            val strokeWidth = 2.dp.toPx()
                            drawCircle(
                                color = outlineColor,
                                radius = (size.minDimension - strokeWidth) / 2,
                                style = Stroke(width = strokeWidth, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 6.dp.toPx()))),
                            )
                        },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Filled.Restaurant,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(40.dp),
                        )
                    }
                } else {
                    FoodPhotoContent(photo = photo, modifier = Modifier.size(PHOTO_DIAMETER))
                }
            }
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(PHOTO_BADGE_DIAMETER)
                    .clip(CircleShape)
                    .background(AppColors.accent)
                    .clickable { isSheetVisible = true },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (photo == FoodItemFormPhoto.None) Icons.Filled.PhotoCamera else Icons.Filled.Cameraswitch,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        Text(
            text = stringResource(if (isMissing) R.string.foodPhoto_error_required else R.string.foodPhoto_title),
            style = MaterialTheme.typography.bodySmall,
            color = if (isMissing) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    if (isSheetVisible) {
        ModalBottomSheet(onDismissRequest = { isSheetVisible = false }) {
            Column(modifier = Modifier.padding(bottom = 16.dp)) {
                if (photo != FoodItemFormPhoto.None) {
                    SheetAction(stringResource(R.string.foodPhoto_action_show)) {
                        isSheetVisible = false
                        isViewerVisible = true
                    }
                }
                if (context.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_CAMERA_ANY)) {
                    SheetAction(stringResource(R.string.foodPhoto_action_take)) {
                        isSheetVisible = false
                        scannerAccess.open()
                    }
                }
                SheetAction(stringResource(R.string.foodPhoto_action_choose)) {
                    isSheetVisible = false
                    libraryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
                SheetAction(stringResource(R.string.common_button_cancel)) { isSheetVisible = false }
            }
        }
    }
    if (isViewerVisible) FoodPhotoViewer(photo = photo, onClose = { isViewerVisible = false })
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
    val directory = File(context.cacheDir, "photos").apply { mkdirs() }
    return File(directory, "${UUID.randomUUID()}.jpg")
}
