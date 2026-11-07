package antoni.kalorie.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import antoni.kalorie.R
import antoni.kalorie.core.models.FoodItemFormPhoto

private val PHOTO_DIAMETER = 120.dp
private val PHOTO_BADGE_DIAMETER = 36.dp

// MARK: - Body

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoodPhotoPicker(
    photo: FoodItemFormPhoto,
    onPhotoChange: (FoodItemFormPhoto) -> Unit,
    modifier: Modifier = Modifier,
    isMissing: Boolean = false,
    onChanged: () -> Unit = {},
) {
    // MARK: - Properties

    val currentOnPhotoChange = rememberUpdatedState(onPhotoChange)
    val currentOnChanged = rememberUpdatedState(onChanged)
    var isSheetVisible by remember { mutableStateOf(false) }
    var isViewerVisible by remember { mutableStateOf(false) }

    val title = stringResource(R.string.foodPhoto_title)
    val outlineColor = if (isMissing) AppColors.error else MaterialTheme.colorScheme.secondary
    val actionDescription = stringResource(if (photo == FoodItemFormPhoto.None) R.string.foodPhoto_action_take else R.string.foodPhoto_accessibility_retake)

    // MARK: - Body

    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(modifier = Modifier.size(PHOTO_DIAMETER).semantics { contentDescription = "$title, $actionDescription" }) {
            Box(modifier = Modifier.size(PHOTO_DIAMETER).clip(CircleShape).clickable { isSheetVisible = true }) {
                if (photo == FoodItemFormPhoto.None) {
                    Box(
                        modifier = Modifier.size(PHOTO_DIAMETER).drawBehind {
                            val strokeWidth = 2.dp.toPx()
                            drawCircle(
                                color = outlineColor,
                                radius = (size.minDimension - strokeWidth) / 2,
                                style = Stroke(width = strokeWidth, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 6.dp.toPx()))),
                            )
                        },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Filled.Restaurant,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(40.dp),
                        )
                    }
                } else {
                    FoodPhotoContent(photo = photo, modifier = Modifier.size(PHOTO_DIAMETER))
                }
            }
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(PHOTO_BADGE_DIAMETER)
                    .clip(CircleShape)
                    .background(AppColors.accent)
                    .clickable { isSheetVisible = true },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (photo == FoodItemFormPhoto.None) Icons.Filled.PhotoCamera else Icons.Filled.Cameraswitch,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        Text(
            text = stringResource(if (isMissing) R.string.foodPhoto_error_required else R.string.foodPhoto_title),
            style = MaterialTheme.typography.bodySmall,
            color = if (isMissing) AppColors.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    FoodPhotoSource(
        isPresented = isSheetVisible,
        onDismiss = { isSheetVisible = false },
        showAction = if (photo != FoodItemFormPhoto.None) ({ isViewerVisible = true }) else null,
    ) { processed ->
        currentOnPhotoChange.value(FoodItemFormPhoto.Local(processed))
        currentOnChanged.value()
    }
    if (isViewerVisible) FoodPhotoViewer(photo = photo, onClose = { isViewerVisible = false })
}
