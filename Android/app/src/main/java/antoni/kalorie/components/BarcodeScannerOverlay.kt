package antoni.kalorie.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import antoni.kalorie.features.addfoodsheet.DataScannerView

@Composable
fun BarcodeScannerOverlay(onScannedCode: (String) -> Unit, isSearching: Boolean, onClose: () -> Unit) {

    // MARK: - Body

    Box(modifier = Modifier.fillMaxSize()) {
        DataScannerView(onScannedCode = onScannedCode, isSearching = isSearching, modifier = Modifier.fillMaxSize())

        IconButton(onClick = onClose, modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)) {
            Icon(Icons.Filled.Close, contentDescription = null, tint = Color.White)
        }

        if (isSearching) {
            Box(
                modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        }
    }
}
