package antoni.kalorie.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import antoni.kalorie.R
import antoni.kalorie.core.models.FoodItemFormPhoto

private const val VIEWER_BACKDROP_ALPHA = 0.5f
private const val VIEWER_SCALE_FROM = 0.6f
private const val VIEWER_SHOW_MILLIS = 250
private const val VIEWER_HIDE_MILLIS = 200
private val VIEWER_HORIZONTAL_PADDING = 16.dp

// MARK: - Body

@Composable
fun FoodPhotoViewer(photo: FoodItemFormPhoto, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val view = LocalView.current
        SideEffect {
            (view.parent as? DialogWindowProvider)?.window?.apply {
                setDimAmount(0f)
                setWindowAnimations(-1)
            }
        }
        FoodPhotoViewerLayout(onClose = onClose) {
            FoodPhotoContent(photo = photo, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
    }
}

@Composable
fun FoodPhotoViewerLayout(onClose: () -> Unit, content: @Composable () -> Unit) {
    val title = stringResource(R.string.foodPhoto_title)
    var isShown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { isShown = true }
    val progress by animateFloatAsState(
        targetValue = if (isShown) 1f else 0f,
        animationSpec = tween(if (isShown) VIEWER_SHOW_MILLIS else VIEWER_HIDE_MILLIS),
        finishedListener = { if (!isShown) onClose() },
        label = "foodPhotoViewer",
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = VIEWER_BACKDROP_ALPHA * progress))
            .clickable { isShown = false }
            .semantics { contentDescription = title },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .padding(horizontal = VIEWER_HORIZONTAL_PADDING)
                .aspectRatio(1f)
                .graphicsLayer {
                    val scale = lerp(VIEWER_SCALE_FROM, 1f, progress)
                    scaleX = scale
                    scaleY = scale
                    alpha = progress
                }
                .clip(CircleShape),
        ) { content() }
    }
}
