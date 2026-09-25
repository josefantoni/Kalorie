package antoni.kalorie.features.addfoodsheet

import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import antoni.kalorie.core.nutritionlabelrecognition.BitmapNutritionLabelImage
import antoni.kalorie.core.nutritionlabelrecognition.MlKitTextRecognizer
import antoni.kalorie.core.nutritionlabelrecognition.NutritionLabelImage
import antoni.kalorie.core.nutritionlabelrecognition.NutritionLabelParser
import antoni.kalorie.core.nutritionlabelrecognition.uprightSize
import antoni.kalorie.core.utils.Constants
import antoni.kalorie.core.utils.Log
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch

private const val PARSE_THROTTLE_MILLIS = 300L

private class LiveScanState {
    @Volatile var liveBarcode: String? = null
    @Volatile var lastParseAtMillis: Long = 0L
}

@OptIn(ExperimentalGetImage::class)
@Composable
fun NutritionLabelScannerView(
    isProcessing: Boolean,
    isAutoCaptureEnabled: Boolean,
    manualCaptureRequest: Int,
    onCaptured: suspend (NutritionLabelImage, String?) -> Unit,
    onCaptureFailed: () -> Unit,
    modifier: Modifier = Modifier,
) {

    // MARK: - Properties

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val currentIsProcessing by rememberUpdatedState(isProcessing)
    val currentIsAutoCaptureEnabled by rememberUpdatedState(isAutoCaptureEnabled)
    val currentOnCaptured by rememberUpdatedState(onCaptured)
    val currentOnCaptureFailed by rememberUpdatedState(onCaptureFailed)
    val executor = remember { Executors.newSingleThreadExecutor() }
    val recognizer = remember { MlKitTextRecognizer() }
    val isCapturing = remember { AtomicBoolean(false) }
    val controller = remember { LifecycleCameraController(context) }
    val liveState = remember { LiveScanState() }
    var lastManualCaptureRequest by remember { mutableIntStateOf(manualCaptureRequest) }

    // isCapturing stays held for the whole round trip (capture through recognition), not just the
    // takePicture call, so a live frame landing in the gap cannot start a second overlapping capture.
    fun capture() {
        if (!isCapturing.compareAndSet(false, true)) return
        val barcode = liveState.liveBarcode
        controller.takePicture(
            executor,
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    val bitmap = image.toBitmap()
                    val rotationDegrees = image.imageInfo.rotationDegrees
                    image.close()
                    scope.launch {
                        try {
                            currentOnCaptured(BitmapNutritionLabelImage(bitmap, rotationDegrees), barcode)
                        } finally {
                            isCapturing.set(false)
                        }
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.warning(exception, Constants.LogCategory.NUTRITION_LABEL_RECOGNITION)
                    isCapturing.set(false)
                    scope.launch { currentOnCaptureFailed() }
                }
            },
        )
    }

    suspend fun processLiveFrame(input: InputImage, width: Int, height: Int, rotationDegrees: Int) {
        if (liveState.liveBarcode == null) liveState.liveBarcode = recognizer.detectBarcode(input)
        if (!currentIsAutoCaptureEnabled) return
        // A genuine label completes over many frames, so re-running the multi-keyword parse on
        // every frame only adds CPU load.
        val now = System.currentTimeMillis()
        if (now - liveState.lastParseAtMillis < PARSE_THROTTLE_MILLIS) return
        liveState.lastParseAtMillis = now
        val (uprightWidth, uprightHeight) = uprightSize(width, height, rotationDegrees)
        val lines = recognizer.recognizeLines(input, uprightWidth, uprightHeight)
        if (NutritionLabelParser.parse(lines).isCompleteForAutoCapture) capture()
    }

    LaunchedEffect(manualCaptureRequest) {
        if (manualCaptureRequest == lastManualCaptureRequest) return@LaunchedEffect
        lastManualCaptureRequest = manualCaptureRequest
        capture()
    }

    DisposableEffect(controller) {
        controller.setEnabledUseCases(CameraController.IMAGE_ANALYSIS or CameraController.IMAGE_CAPTURE)
        controller.imageCaptureMode = ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY
        controller.imageAnalysisBackpressureStrategy = ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
        val analysisScope = CoroutineScope(executor.asCoroutineDispatcher())
        controller.setImageAnalysisAnalyzer(executor) { imageProxy ->
            val mediaImage = imageProxy.image
            if (mediaImage == null || currentIsProcessing || isCapturing.get()) {
                imageProxy.close()
                return@setImageAnalysisAnalyzer
            }
            val rotationDegrees = imageProxy.imageInfo.rotationDegrees
            val input = InputImage.fromMediaImage(mediaImage, rotationDegrees)
            analysisScope.launch {
                try {
                    processLiveFrame(input, mediaImage.width, mediaImage.height, rotationDegrees)
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    Log.warning(error, Constants.LogCategory.NUTRITION_LABEL_RECOGNITION)
                } finally {
                    imageProxy.close()
                }
            }
        }
        controller.bindToLifecycle(lifecycleOwner)
        onDispose {
            controller.clearImageAnalysisAnalyzer()
            controller.unbind()
            recognizer.close()
            executor.shutdown()
        }
    }

    // MARK: - Body

    AndroidView(
        modifier = modifier,
        factory = { viewContext -> PreviewView(viewContext).also { it.controller = controller } },
    )
}
