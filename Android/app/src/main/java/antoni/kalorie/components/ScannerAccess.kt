package antoni.kalorie.components

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

class ScannerAccess(val isAvailable: () -> Boolean, val open: () -> Unit)

@Composable
fun rememberScannerAccess(onGranted: () -> Unit, onDenied: () -> Unit): ScannerAccess {

    // MARK: - Properties

    val context = LocalContext.current
    val currentOnGranted = rememberUpdatedState(onGranted)
    val currentOnDenied = rememberUpdatedState(onDenied)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) currentOnGranted.value() else currentOnDenied.value()
    }

    // MARK: - Body

    return remember(context, launcher) {
        val isAvailable = { ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED }
        ScannerAccess(isAvailable = isAvailable) {
            if (isAvailable()) currentOnGranted.value() else launcher.launch(Manifest.permission.CAMERA)
        }
    }
}
