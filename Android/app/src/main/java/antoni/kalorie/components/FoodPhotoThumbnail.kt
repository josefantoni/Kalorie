package antoni.kalorie.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
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
private const val THUMBNAIL_TRANSITION_KEY = "foodPhoto"

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
    SharedTransitionLayout {
        Box(modifier = Modifier.fillMaxSize()) {
            content {
                if (url != null && !state.hasFailed) {
                    AnimatedVisibility(visible = !state.isViewerVisible, enter = EnterTransition.None, exit = ExitTransition.None) {
                        FoodPhotoThumbnail(
                            url = url,
                            sharedTransitionScope = this@SharedTransitionLayout,
                            animatedVisibilityScope = this,
                            onTapped = { state.isViewerVisible = true },
                            onFailed = { state.hasFailed = true },
                        )
                    }
                }
            }
            if (url != null) {
                AnimatedVisibility(visible = state.isViewerVisible, enter = fadeIn(), exit = fadeOut()) {
                    FoodPhotoOverlay(
                        url = url,
                        sharedTransitionScope = this@SharedTransitionLayout,
                        animatedVisibilityScope = this,
                        onClose = { state.isViewerVisible = false },
                    )
                }
            }
        }
    }
}

@Composable
private fun FoodPhotoThumbnail(
    url: String,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onTapped: () -> Unit,
    onFailed: () -> Unit,
) {
    val enlarge = stringResource(R.string.foodPhoto_accessibility_enlarge)
    val width = thumbnailWidth()
    with(sharedTransitionScope) {
        SubcomposeAsyncImage(
            model = url,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            onError = { onFailed() },
            loading = {
                Box(
                    modifier = Modifier.size(width = width, height = THUMBNAIL_HEIGHT).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator(modifier = Modifier.size(24.dp)) }
            },
            error = {},
            modifier = Modifier
                .sharedBounds(
                    sharedContentState = rememberSharedContentState(THUMBNAIL_TRANSITION_KEY),
                    animatedVisibilityScope = animatedVisibilityScope,
                )
                .width(width)
                .height(THUMBNAIL_HEIGHT)
                .clip(CircleShape)
                .clickable(onClick = onTapped)
                .semantics { contentDescription = enlarge },
        )
    }
}

@Composable
private fun FoodPhotoOverlay(
    url: String,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onClose: () -> Unit,
) {
    val title = stringResource(R.string.foodPhoto_title)
    with(sharedTransitionScope) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .clickable(onClick = onClose)
                .semantics { contentDescription = title },
            contentAlignment = Alignment.Center,
        ) {
            FoodPhotoRemoteContent(
                url = url,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .sharedBounds(
                        sharedContentState = rememberSharedContentState(THUMBNAIL_TRANSITION_KEY),
                        animatedVisibilityScope = animatedVisibilityScope,
                    )
                    .fillMaxSize(),
            )
        }
    }
}

@Composable
private fun thumbnailWidth(): Dp {
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    return (screenWidth - THUMBNAIL_SIDE_RESERVE * 2).coerceIn(0.dp, THUMBNAIL_MAX_WIDTH)
}
