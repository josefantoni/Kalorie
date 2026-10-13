package antoni.kalorie.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import antoni.kalorie.R
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.roundToInt

// MARK: - Properties

private val ACTION_WIDTH = 80.dp
private const val FULL_SWIPE_FRACTION = 0.5f
private const val FULL_SWIPE_VELOCITY = 1_000f

// MARK: - Body

@Composable
fun RevealingSwipeToDeleteRow(
    id: String,
    openRowId: String?,
    onOpenRowChange: (String?) -> Unit,
    onDeleteRequested: () -> Unit,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    content: @Composable () -> Unit,
) {
    val currentOnDeleteRequested by rememberUpdatedState(onDeleteRequested)
    val currentOpenRowId by rememberUpdatedState(openRowId)
    val currentOnOpenRowChange by rememberUpdatedState(onOpenRowChange)
    val scope = rememberCoroutineScope()
    val actionWidthPx = with(LocalDensity.current) { ACTION_WIDTH.toPx() }
    val offset = remember { Animatable(0f) }
    var rowWidthPx by remember { mutableFloatStateOf(0f) }
    var lastDragVelocity by remember { mutableFloatStateOf(0f) }
    val dragInteractionSource = remember { MutableInteractionSource() }
    val isDragged by dragInteractionSource.collectIsDraggedAsState()
    val deleteLabel = stringResource(R.string.common_button_delete)

    LaunchedEffect(openRowId) {
        if (openRowId != id && offset.value != 0f) offset.animateTo(0f)
    }

    LaunchedEffect(isDragged) {
        if (isDragged) return@LaunchedEffect
        val isAtRest = offset.value == 0f || offset.value == -actionWidthPx
        if (isAtRest) return@LaunchedEffect
        val isFullSwipe = offset.value < -rowWidthPx * FULL_SWIPE_FRACTION ||
            (lastDragVelocity < -FULL_SWIPE_VELOCITY && offset.value < -actionWidthPx)
        if (isFullSwipe) {
            if (currentOpenRowId == id) currentOnOpenRowChange(null)
            currentOnDeleteRequested()
            offset.animateTo(0f)
        } else {
            val isOpen = offset.value < -actionWidthPx / 2
            currentOnOpenRowChange(if (isOpen) id else null)
            offset.animateTo(if (isOpen) -actionWidthPx else 0f)
        }
    }

    fun close() {
        if (currentOpenRowId == id) currentOnOpenRowChange(null)
        scope.launch { offset.animateTo(0f) }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clipToBounds()
            .onSizeChanged { rowWidthPx = it.width.toFloat() },
    ) {
        Box(
            modifier = Modifier.matchParentSize(),
            contentAlignment = Alignment.CenterEnd,
        ) {
            val revealedWidth = max(actionWidthPx, -offset.value)
            Box(
                modifier = Modifier
                    .width(with(LocalDensity.current) { revealedWidth.toDp() })
                    .fillMaxHeight()
                    .background(AppColors.error)
                    .clickable(enabled = -offset.value >= actionWidthPx * 0.95f) {
                        close()
                        currentOnDeleteRequested()
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.Delete, contentDescription = null, tint = Color.White)
            }
        }
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(offset.value.roundToInt(), 0) }
                .semantics {
                    customActions = listOf(
                        CustomAccessibilityAction(deleteLabel) {
                            currentOnDeleteRequested()
                            true
                        },
                    )
                }
                .draggable(
                    state = rememberDraggableState { delta ->
                        scope.launch { offset.snapTo((offset.value + delta).coerceIn(-rowWidthPx, 0f)) }
                    },
                    orientation = Orientation.Horizontal,
                    onDragStarted = {
                        if (currentOpenRowId != id) currentOnOpenRowChange(null)
                    },
                    onDragStopped = { velocity -> lastDragVelocity = velocity },
                    interactionSource = dragInteractionSource,
                ),
            color = containerColor,
        ) {
            content()
        }
    }
}
