package antoni.kalorie.features.addfoodsheet

import android.os.SystemClock
import android.util.Size
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import antoni.kalorie.components.FoodItemFormField
import antoni.kalorie.core.nutritionlabelrecognition.MlKitTextRecognizer
import antoni.kalorie.core.nutritionlabelrecognition.NutritionLabelParser
import antoni.kalorie.core.nutritionlabelrecognition.NutritionLabelReading
import antoni.kalorie.core.nutritionlabelrecognition.NutritionLabelReadingMerger
import antoni.kalorie.core.nutritionlabelrecognition.uprightSize
import antoni.kalorie.core.utils.Constants
import antoni.kalorie.core.utils.Log
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

private const val ANALYSIS_WIDTH = 1920
private const val ANALYSIS_HEIGHT = 1440
private const val SCAN_WINDOW_MILLIS = 3000L

private class LiveScanState {
    @Volatile var liveBarcode: String? = null

    @Volatile var windowStartedAtMillis: Long = 0L

    val readings = mutableListOf<NutritionLabelReading>()
}

@OptIn(ExperimentalGetImage::class)
@Composable
fun NutritionLabelScannerView(
    onProgress: (Float) -> Unit,
    onRecognized: (NutritionLabelReading, String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    // MARK: - Properties

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnProgress by rememberUpdatedState(onProgress)
    val currentOnRecognized by rememberUpdatedState(onRecognized)
    val executor = remember { Executors.newSingleThreadExecutor() }
    val recognizer = remember { MlKitTextRecognizer() }
    val controller = remember { LifecycleCameraController(context) }
    val liveState = remember { LiveScanState() }

    // The window starts at the first frame that shows part of a table and restarts whenever a frame shows none;
    // the user holds the phone over the label for SCAN_WINDOW_MILLIS, every live frame is read and the readings
    // are merged at the end, so a digit misread in one frame is outvoted by the others.
    suspend fun processLiveFrame(input: InputImage, width: Int, height: Int, rotationDegrees: Int) {
        if (liveState.liveBarcode == null) liveState.liveBarcode = recognizer.detectBarcode(input)
        val (uprightWidth, uprightHeight) = uprightSize(width, height, rotationDegrees)
        val reading = NutritionLabelParser.parse(recognizer.recognizeLines(input, uprightWidth, uprightHeight))
        val now = SystemClock.elapsedRealtime()
        if (reading.recognizedFields.none { it != FoodItemFormField.MEASURE }) {
            liveState.readings.clear()
            liveState.windowStartedAtMillis = 0L
            withContext(Dispatchers.Main) { currentOnProgress(0f) }
            return
        }
        if (liveState.windowStartedAtMillis == 0L) liveState.windowStartedAtMillis = now
        liveState.readings.add(reading)
        val elapsed = now - liveState.windowStartedAtMillis
        if (elapsed < SCAN_WINDOW_MILLIS) {
            withContext(Dispatchers.Main) { currentOnProgress(elapsed.toFloat() / SCAN_WINDOW_MILLIS) }
            return
        }
        val merged = NutritionLabelReadingMerger.merge(liveState.readings.toList())
        liveState.readings.clear()
        liveState.windowStartedAtMillis = 0L
        withContext(Dispatchers.Main) {
            currentOnProgress(0f)
            currentOnRecognized(merged, liveState.liveBarcode)
        }
    }

    DisposableEffect(controller) {
        controller.setEnabledUseCases(CameraController.IMAGE_ANALYSIS)
        controller.imageAnalysisBackpressureStrategy = ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
        controller.imageAnalysisResolutionSelector = ResolutionSelector.Builder()
            .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
            .setResolutionStrategy(ResolutionStrategy(Size(ANALYSIS_WIDTH, ANALYSIS_HEIGHT), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER))
            .build()
        val analysisScope = CoroutineScope(executor.asCoroutineDispatcher())
        controller.setImageAnalysisAnalyzer(executor) { imageProxy ->
            val mediaImage = imageProxy.image
            if (mediaImage == null) {
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
            analysisScope.cancel()
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
