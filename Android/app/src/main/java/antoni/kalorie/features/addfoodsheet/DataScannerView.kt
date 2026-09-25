package antoni.kalorie.features.addfoodsheet

import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.UseCase
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors

private class BoundCamera {
    var provider: ProcessCameraProvider? = null
    var useCases: List<UseCase> = emptyList()
    var isDisposed = false
}

@OptIn(ExperimentalGetImage::class)
@Composable
fun DataScannerView(onScannedCode: (String) -> Unit, isSearching: Boolean, modifier: Modifier = Modifier) {

    // MARK: - Properties

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnScannedCode by rememberUpdatedState(onScannedCode)
    val currentIsSearching by rememberUpdatedState(isSearching)
    var lastDeliveredCode by remember { mutableStateOf<String?>(null) }
    var wasSearching by remember { mutableStateOf(false) }
    val executor = remember { Executors.newSingleThreadExecutor() }
    val scanner = remember { BarcodeScanning.getClient() }
    val boundCamera = remember { BoundCamera() }

    LaunchedEffect(isSearching) {
        if (wasSearching && !isSearching) lastDeliveredCode = null
        wasSearching = isSearching
    }

    DisposableEffect(Unit) {
        onDispose {
            boundCamera.isDisposed = true
            boundCamera.provider?.unbind(*boundCamera.useCases.toTypedArray())
            scanner.close()
            executor.shutdown()
        }
    }

    // MARK: - Body

    AndroidView(
        modifier = modifier,
        factory = { viewContext ->
            val previewView = PreviewView(viewContext)
            val providerFuture = ProcessCameraProvider.getInstance(viewContext)
            providerFuture.addListener(
                {
                    if (boundCamera.isDisposed) return@addListener
                    val provider = providerFuture.get()
                    val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
                    val analysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                    analysis.setAnalyzer(executor) { imageProxy ->
                        val mediaImage = imageProxy.image
                        if (mediaImage == null || currentIsSearching) {
                            imageProxy.close()
                            return@setAnalyzer
                        }
                        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                        scanner.process(image)
                            .addOnSuccessListener { barcodes ->
                                val code = barcodes.firstNotNullOfOrNull { it.rawValue }
                                if (code != null && code != lastDeliveredCode) {
                                    lastDeliveredCode = code
                                    currentOnScannedCode(code)
                                }
                            }
                            .addOnCompleteListener { imageProxy.close() }
                    }
                    provider.unbindAll()
                    provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                    boundCamera.provider = provider
                    boundCamera.useCases = listOf(preview, analysis)
                },
                ContextCompat.getMainExecutor(viewContext),
            )
            previewView
        },
    )
}
