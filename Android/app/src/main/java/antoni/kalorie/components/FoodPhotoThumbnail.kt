package antoni.kalorie.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import antoni.kalorie.R
import coil3.compose.SubcomposeAsyncImage

private val THUMBNAIL_HEIGHT = 44.dp
private val THUMBNAIL_SIDE_RESERVE = 132.dp
private val THUMBNAIL_MAX_WIDTH = 320.dp
private const val THUMBNAIL_REVEAL_MILLIS = 350

class FoodPhotoThumbnailState(val url: String?) {
    var hasFailed by mutableStateOf(false)
    var isViewerVisible by mutableStateOf(false)
}

@Composable
fun rememberFoodPhotoThumbnailState(url: String?): FoodPhotoThumbnailState = remember(url) { FoodPhotoThumbnailState(url) }

// MARK: - Body

@Composable
fun FoodPhotoTransitionHost(state: FoodPhotoThumbnailState, content: @Composable (photoTitle: @Composable () -> Unit) -> Unit) {
    val url = state.url
    BackHandler(enabled = state.isViewerVisible) { state.isViewerVisible = false }
    Box(modifier = Modifier.fillMaxSize()) {
        content {
            if (url != null && !state.hasFailed) {
                FoodPhotoThumbnail(
                    url = url,
                    onTapped = { state.isViewerVisible = true },
                    onFailed = { state.hasFailed = true },
                )
            }
        }
        if (url != null && state.isViewerVisible) {
            FoodPhotoViewerLayout(onClose = { state.isViewerVisible = false }) {
                FoodPhotoRemoteContent(url = url, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
        }
    }
}

@Composable
private fun FoodPhotoThumbnail(url: String, onTapped: () -> Unit, onFailed: () -> Unit) {
    val enlarge = stringResource(R.string.foodPhoto_accessibility_enlarge)
    val width = thumbnailWidth()
    var isLoaded by remember { mutableStateOf(false) }
    val reveal by animateFloatAsState(
        targetValue = if (isLoaded) 1f else 0f,
        animationSpec = tween(THUMBNAIL_REVEAL_MILLIS),
        label = "foodPhotoReveal",
    )
    SubcomposeAsyncImage(
        model = url,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        onSuccess = { isLoaded = true },
        onError = { onFailed() },
        loading = { Box(modifier = Modifier.size(width = width, height = THUMBNAIL_HEIGHT)) },
        error = {},
        modifier = Modifier
            .width(width)
            .height(THUMBNAIL_HEIGHT)
            .graphicsLayer {
                scaleX = reveal
                scaleY = reveal
            }
            .clip(CircleShape)
            .clickable(onClick = onTapped)
            .semantics { contentDescription = enlarge },
    )
}

@Composable
private fun thumbnailWidth(): Dp {
    val screenWidth = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.width.toDp() }
    return (screenWidth - THUMBNAIL_SIDE_RESERVE * 2).coerceIn(0.dp, THUMBNAIL_MAX_WIDTH)
}
