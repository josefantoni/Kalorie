package antoni.kalorie.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import antoni.kalorie.R
import antoni.kalorie.core.models.FoodItemFormPhoto
import coil3.compose.SubcomposeAsyncImage

private val THUMBNAIL_DIAMETER = 60.dp
private val THUMBNAIL_BADGE_DIAMETER = 24.dp
private val THUMBNAIL_BOTTOM_SPACE = THUMBNAIL_DIAMETER + 16.dp

class FoodPhotoThumbnailState(val url: String?) {
    var hasFailed by mutableStateOf(false)
}

@Composable
fun rememberFoodPhotoThumbnailState(url: String?): FoodPhotoThumbnailState = remember(url) { FoodPhotoThumbnailState(url) }

// MARK: - Body

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BoxScope.FoodPhotoThumbnailOverlay(state: FoodPhotoThumbnailState) {
    val url = state.url
    var isViewerVisible by remember { mutableStateOf(false) }
    if (url != null && !WindowInsets.isImeVisible) {
        FoodPhotoThumbnail(
            url = url,
            onTapped = { isViewerVisible = true },
            onFailed = { state.hasFailed = true },
            modifier = Modifier.align(Alignment.BottomStart),
        )
    }
    if (isViewerVisible && url != null) {
        FoodPhotoViewer(photo = FoodItemFormPhoto.Remote(url), onClose = { isViewerVisible = false })
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FoodPhotoThumbnailBottomSpace(state: FoodPhotoThumbnailState) {
    if (state.url != null && !state.hasFailed && !WindowInsets.isImeVisible) {
        Spacer(modifier = Modifier.height(THUMBNAIL_BOTTOM_SPACE))
    }
}

@Composable
private fun FoodPhotoThumbnail(url: String, onTapped: () -> Unit, onFailed: () -> Unit, modifier: Modifier = Modifier) {
    val enlarge = stringResource(R.string.foodPhoto_accessibility_enlarge)
    SubcomposeAsyncImage(
        model = url,
        contentDescription = null,
        modifier = modifier,
        onError = { onFailed() },
        loading = {
            Box(
                modifier = Modifier
                    .padding(start = 20.dp, bottom = 8.dp)
                    .size(THUMBNAIL_DIAMETER)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator(modifier = Modifier.size(24.dp)) }
        },
        error = {},
        success = { state ->
            Box(
                modifier = Modifier
                    .padding(start = 20.dp, bottom = 8.dp)
                    .size(THUMBNAIL_DIAMETER)
                    .clickable(onClick = onTapped)
                    .semantics { contentDescription = enlarge },
            ) {
                Image(
                    painter = state.painter,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(THUMBNAIL_DIAMETER).clip(CircleShape),
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(THUMBNAIL_BADGE_DIAMETER)
                        .clip(CircleShape)
                        .background(AppColors.accent),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.OpenInFull, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                }
            }
        },
    )
}
