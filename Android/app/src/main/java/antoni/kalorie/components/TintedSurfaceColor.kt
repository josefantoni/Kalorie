package antoni.kalorie.components

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.unit.dp

// MARK: - Body

@Composable
fun tintedSurfaceColor(): Color = if (isSystemInDarkTheme()) {
    MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
} else {
    MaterialTheme.colorScheme.primary.copy(alpha = 0.2f).compositeOver(Color.White)
}
