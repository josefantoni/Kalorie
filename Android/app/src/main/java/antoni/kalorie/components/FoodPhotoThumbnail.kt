package antoni.kalorie.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RichTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
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
import antoni.kalorie.core.utils.Constants
import coil3.compose.SubcomposeAsyncImage
import kotlinx.coroutines.launch

private val THUMBNAIL_HEIGHT = 44.dp
private val THUMBNAIL_SIDE_RESERVE = 132.dp
private val THUMBNAIL_MAX_WIDTH = 320.dp
private const val THUMBNAIL_REVEAL_MILLIS = 350

sealed interface FoodPhotoPillState {
    data object Hidden : FoodPhotoPillState
    data class Photo(val url: String) : FoodPhotoPillState
    data object Empty : FoodPhotoPillState
    data object AwaitingApproval : FoodPhotoPillState
}

class FoodPhotoThumbnailState(val pill: FoodPhotoPillState, val onSend: suspend (ByteArray) -> Unit) {
    val url: String? get() = (pill as? FoodPhotoPillState.Photo)?.url
    var hasFailed by mutableStateOf(false)
    var isViewerVisible by mutableStateOf(false)
}

@Composable
fun rememberFoodPhotoThumbnailState(pill: FoodPhotoPillState, onSend: suspend (ByteArray) -> Unit): FoodPhotoThumbnailState {
    val currentOnSend = rememberUpdatedState(onSend)
    return remember(pill) { FoodPhotoThumbnailState(pill) { currentOnSend.value(it) } }
}

// MARK: - Body

@Composable
fun FoodPhotoTransitionHost(state: FoodPhotoThumbnailState, content: @Composable (photoTitle: @Composable () -> Unit) -> Unit) {
    val url = state.url
    BackHandler(enabled = state.isViewerVisible) { state.isViewerVisible = false }
    Box(modifier = Modifier.fillMaxSize()) {
        content {
            when (val pill = state.pill) {
                is FoodPhotoPillState.Hidden -> Unit
                is FoodPhotoPillState.Photo -> if (!state.hasFailed) {
                    FoodPhotoThumbnail(
                        url = pill.url,
                        onTapped = { state.isViewerVisible = true },
                        onFailed = { state.hasFailed = true },
                    )
                }
                is FoodPhotoPillState.Empty -> FoodPhotoSuggestionPill(onSend = state.onSend)
                is FoodPhotoPillState.AwaitingApproval -> FoodPhotoAwaitingPill()
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
private fun FoodPhotoPillFrame(
    isEnabled: Boolean,
    contentDescription: String,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    val width = thumbnailWidth()
    val outline = MaterialTheme.colorScheme.secondary
    Box(
        modifier = Modifier
            .width(width)
            .height(THUMBNAIL_HEIGHT)
            .clip(CircleShape)
            .drawBehind {
                val strokeWidth = 2.dp.toPx()
                drawRoundRect(
                    color = outline,
                    topLeft = Offset(strokeWidth / 2, strokeWidth / 2),
                    size = Size(size.width - strokeWidth, size.height - strokeWidth),
                    cornerRadius = CornerRadius(size.height / 2),
                    style = Stroke(width = strokeWidth, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 6.dp.toPx()))),
                )
            }
            .clickable(enabled = isEnabled, onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
private fun FoodPhotoSuggestionPill(onSend: suspend (ByteArray) -> Unit) {
    var isSourceVisible by remember { mutableStateOf(false) }
    var isSending by remember { mutableStateOf(false) }
    FoodPhotoPillFrame(
        isEnabled = !isSending,
        contentDescription = stringResource(R.string.foodPhoto_suggestion_add),
        onClick = { isSourceVisible = true },
    ) {
        if (isSending) {
            CircularProgressIndicator(
                modifier = Modifier.size(Constants.Loader.SPINNER_SIZE),
                color = AppColors.accent,
                strokeWidth = Constants.Loader.STROKE_WIDTH,
            )
        } else {
            Box(
                modifier = Modifier.size(30.dp).clip(CircleShape).background(AppColors.accent),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.PhotoCamera, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            }
        }
    }
    FoodPhotoSource(
        isPresented = isSourceVisible,
        onDismiss = { isSourceVisible = false },
        onProcessingChange = { isSending = it },
    ) { processed ->
        onSend(processed)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FoodPhotoAwaitingPill() {
    val tooltipState = rememberTooltipState(isPersistent = true)
    val scope = rememberCoroutineScope()
    val message = stringResource(R.string.foodPhoto_suggestion_awaiting)
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { RichTooltip { Text(message) } },
        state = tooltipState,
        enableUserInput = false,
    ) {
        FoodPhotoPillFrame(
            isEnabled = true,
            contentDescription = message,
            onClick = { scope.launch { tooltipState.show() } },
        ) {
            Icon(Icons.Filled.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
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
