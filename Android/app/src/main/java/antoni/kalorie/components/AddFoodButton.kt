package antoni.kalorie.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import antoni.kalorie.R

val ADD_FOOD_BUTTON_EXPANDED_SIZE = 140.dp
const val ADD_FOOD_BUTTON_RESIZE_MILLIS = 400

private val COLLAPSED_SIZE = 56.dp
private val ANIMATION_PADDING = 10.dp
private const val PLUS_FADE_MILLIS = 300

@Composable
fun AddFoodButton(
    isExpanded: Boolean,
    isAnimationPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource? = null,
) {
    // MARK: - Properties

    val size by animateDpAsState(
        targetValue = if (isExpanded) ADD_FOOD_BUTTON_EXPANDED_SIZE else COLLAPSED_SIZE,
        animationSpec = tween(ADD_FOOD_BUTTON_RESIZE_MILLIS),
        label = "addFoodButtonSize",
    )
    val plusAlpha by animateFloatAsState(
        targetValue = if (isAnimationPlaying) 0f else 1f,
        animationSpec = tween(PLUS_FADE_MILLIS),
        label = "addFoodButtonPlusAlpha",
    )

    // MARK: - Body

    FloatingActionButton(
        onClick = onClick,
        interactionSource = interactionSource,
        shape = CircleShape,
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        modifier = modifier.size(size),
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(
                Icons.Filled.Add,
                contentDescription = stringResource(R.string.dashboard_empty_addFood),
                modifier = Modifier.alpha(plusAlpha),
            )
            if (isAnimationPlaying) {
                PancakeSyrupAnimation(modifier = Modifier.fillMaxSize().padding(ANIMATION_PADDING))
            }
        }
    }
}
