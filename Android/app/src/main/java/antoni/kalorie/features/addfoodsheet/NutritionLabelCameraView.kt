package antoni.kalorie.features.addfoodsheet

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import antoni.kalorie.R
import antoni.kalorie.core.nutritionlabelrecognition.NutritionLabelReading

@Composable
fun NutritionLabelCameraView(
    hint: String?,
    onRecognized: (NutritionLabelReading, String?) -> Unit,
    onClose: () -> Unit,
) {
    // MARK: - Properties

    var progress by remember { mutableFloatStateOf(0f) }

    // MARK: - Body

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        NutritionLabelScannerView(
            onProgress = { progress = it },
            onRecognized = onRecognized,
            modifier = Modifier.fillMaxSize(),
        )

        IconButton(onClick = onClose, modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)) {
            Icon(Icons.Filled.Close, contentDescription = null, tint = Color.White)
        }

        Column(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = hint ?: stringResource(R.string.addFood_nutritionLabel_cameraIdleHint),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            )
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.padding(top = 16.dp).fillMaxWidth(0.6f),
            )
        }
    }
}
