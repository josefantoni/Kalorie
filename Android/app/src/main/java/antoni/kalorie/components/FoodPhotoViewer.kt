package antoni.kalorie.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import antoni.kalorie.R
import antoni.kalorie.core.models.FoodItemFormPhoto

private const val CLOSE_DRAG_THRESHOLD = 80f

// MARK: - Body

@Composable
fun FoodPhotoViewer(photo: FoodItemFormPhoto, onClose: () -> Unit) {
    val title = stringResource(R.string.foodPhoto_title)
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .clickable(onClick = onClose)
                .pointerInput(Unit) {
                    var totalDrag = 0f
                    detectVerticalDragGestures(
                        onDragStart = { totalDrag = 0f },
                        onDragEnd = { if (totalDrag > CLOSE_DRAG_THRESHOLD) onClose() },
                    ) { _, dragAmount -> totalDrag += dragAmount }
                }
                .semantics { contentDescription = title },
            contentAlignment = Alignment.Center,
        ) {
            FoodPhotoContent(photo = photo, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
        }
    }
}
