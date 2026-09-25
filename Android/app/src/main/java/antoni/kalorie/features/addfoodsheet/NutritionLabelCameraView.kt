package antoni.kalorie.features.addfoodsheet

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import antoni.kalorie.R
import antoni.kalorie.core.nutritionlabelrecognition.NutritionLabelImage

@Composable
fun NutritionLabelCameraView(
    isRecognizing: Boolean,
    hint: String?,
    onCaptured: suspend (NutritionLabelImage, String?) -> Unit,
    onClose: () -> Unit,
) {

    // MARK: - Properties

    var manualCaptureRequest by remember { mutableIntStateOf(0) }
    var scannerResetToken by remember { mutableIntStateOf(0) }
    var captureFailureMessage by remember { mutableStateOf<String?>(null) }
    var isAutoCaptureEnabled by remember { mutableStateOf(true) }
    val captureFailedText = stringResource(R.string.addFood_nutritionLabel_cameraCaptureFailed)
    val shutterDescription = stringResource(R.string.addFood_nutritionLabel_cameraShutterAccessibility)

    LaunchedEffect(isRecognizing) { if (isRecognizing) captureFailureMessage = null }

    // MARK: - Body

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // Auto-capture is disabled after a failure, not just retried: on a device where the capture
        // itself fails, the next live frame re-clears the same predicate and retriggers it at once,
        // turning one failure into a tight fail/reset loop. The shutter stays as a user-paced retry.
        key(scannerResetToken) {
            NutritionLabelScannerView(
                isProcessing = isRecognizing,
                isAutoCaptureEnabled = isAutoCaptureEnabled,
                manualCaptureRequest = manualCaptureRequest,
                onCaptured = onCaptured,
                onCaptureFailed = {
                    captureFailureMessage = captureFailedText
                    isAutoCaptureEnabled = false
                    scannerResetToken += 1
                },
                modifier = Modifier.fillMaxSize(),
            )
        }

        // The progress overlay renders below the controls, so it can never swallow the close button.
        if (isRecognizing) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        }

        IconButton(onClick = onClose, modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)) {
            Icon(Icons.Filled.Close, contentDescription = null, tint = Color.White)
        }

        Column(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = captureFailureMessage ?: hint ?: stringResource(R.string.addFood_nutritionLabel_cameraIdleHint),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            )
            OutlinedButton(
                onClick = { manualCaptureRequest += 1 },
                enabled = !isRecognizing,
                shape = CircleShape,
                border = BorderStroke(4.dp, Color.White),
                modifier = Modifier
                    .padding(top = 16.dp, bottom = 24.dp)
                    .size(72.dp)
                    .semantics { contentDescription = shutterDescription },
            ) {
                Box(modifier = Modifier.size(50.dp).background(Color.White, CircleShape))
            }
        }
    }
}
