package antoni.kalorie.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import antoni.kalorie.core.utils.Constants

// MARK: - Body

@Composable
fun LoaderIndicator(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(Constants.Loader.SIZE)
            .background(MaterialTheme.colorScheme.background, RoundedCornerShape(Constants.Loader.CORNER_RADIUS)),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(Constants.Loader.SPINNER_SIZE),
            color = AppColors.accent,
            strokeWidth = Constants.Loader.STROKE_WIDTH,
        )
    }
}
