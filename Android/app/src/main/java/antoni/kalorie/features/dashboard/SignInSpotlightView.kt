package antoni.kalorie.features.dashboard

import android.view.accessibility.AccessibilityManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.dismiss
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import antoni.kalorie.R
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private const val AUTO_DISMISS_MILLIS = 10_000L
private const val DIM_ALPHA = 0.4f
private val CutoutPadding = 6.dp
private val ArrowWidth = 20.dp
private val ArrowHeight = 10.dp
private val BubbleCornerRadius = 16.dp
private val BubbleMaxWidth = 300.dp
private val ScreenMargin = 8.dp

@Composable
fun SignInSpotlightView(
    targetBounds: Rect,
    onSignIn: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // MARK: - Properties

    val context = LocalContext.current
    val density = LocalDensity.current
    val screenWidth = with(density) { LocalWindowInfo.current.containerSize.width.toDp() }
    var overlayOrigin by remember { mutableStateOf(Offset.Zero) }
    val titleFocusRequester = remember { FocusRequester() }
    val cutout = spotlightCutout(targetBounds.translate(-overlayOrigin.x, -overlayOrigin.y), with(density) { CutoutPadding.toPx() })
    val bubbleWidth = minOf(BubbleMaxWidth, screenWidth - ScreenMargin * 2)
    val screenMarginPx = with(density) { ScreenMargin.toPx() }
    val bubbleWidthPx = with(density) { bubbleWidth.toPx() }
    val arrowWidthPx = with(density) { ArrowWidth.toPx() }
    val idealBubbleX = cutout.center.x - arrowWidthPx / 2 - with(density) { BubbleCornerRadius.toPx() }
    val bubbleX = idealBubbleX.coerceIn(screenMarginPx, maxOf(screenMarginPx, with(density) { screenWidth.toPx() } - bubbleWidthPx - screenMarginPx))
    val arrowX = (cutout.center.x - bubbleX - arrowWidthPx / 2).coerceIn(0f, bubbleWidthPx - arrowWidthPx)
    val surfaceColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val signInLabel = stringResource(R.string.dashboard_signInSpotlight_actionSignIn)

    LaunchedEffect(Unit) {
        titleFocusRequester.requestFocus()
        val accessibilityManager = context.getSystemService(AccessibilityManager::class.java)
        if (accessibilityManager?.isTouchExplorationEnabled == true) return@LaunchedEffect
        delay(AUTO_DISMISS_MILLIS)
        onDismiss()
    }

    // MARK: - Body

    Box(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { overlayOrigin = it.positionInWindow() }
            .pointerInput(cutout) {
                detectTapGestures { position -> if (cutout.contains(position)) onSignIn() else onDismiss() }
            },
    ) {
        Canvas(modifier = Modifier.fillMaxSize().graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)) {
            drawRect(Color.Black.copy(alpha = DIM_ALPHA))
            drawCircle(Color.Black, radius = cutout.width / 2, center = cutout.center, blendMode = BlendMode.Clear)
        }

        Column(
            modifier = Modifier
                .width(bubbleWidth)
                .offset { IntOffset(bubbleX.roundToInt(), (cutout.bottom + with(density) { 4.dp.toPx() }).roundToInt()) },
        ) {
            Canvas(modifier = Modifier.padding(start = with(density) { arrowX.toDp() }).size(ArrowWidth, ArrowHeight)) {
                val arrow = Path().apply {
                    moveTo(size.width / 2, 0f)
                    lineTo(size.width, size.height)
                    lineTo(0f, size.height)
                    close()
                }
                drawPath(arrow, surfaceColor)
            }
            Surface(
                shape = RoundedCornerShape(BubbleCornerRadius),
                color = surfaceColor,
                modifier = Modifier
                    .offset(y = (-2).dp)
                    .semantics(mergeDescendants = true) {
                        onClick(label = signInLabel) {
                            onSignIn()
                            true
                        }
                        dismiss {
                            onDismiss()
                            true
                        }
                    },
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.dashboard_signInSpotlight_title),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier
                            .focusRequester(titleFocusRequester)
                            .focusable()
                            .semantics { heading() },
                    )
                    Text(
                        text = stringResource(R.string.dashboard_signInSpotlight_message),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private fun spotlightCutout(target: Rect, paddingPx: Float): Rect {
    val diameter = maxOf(target.width, target.height) + paddingPx * 2
    return Rect(Offset(target.center.x - diameter / 2, target.center.y - diameter / 2), Size(diameter, diameter))
}
