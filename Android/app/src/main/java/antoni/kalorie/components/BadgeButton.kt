package antoni.kalorie.components

import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// MARK: - Body

@Composable
fun BadgeButton(
    icon: ImageVector,
    onClick: () -> Unit,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    size: Dp = BADGE_BUTTON_SIZE,
) {
    FilledIconButton(onClick = onClick, enabled = enabled, modifier = modifier.size(size)) {
        Icon(icon, contentDescription = contentDescription, modifier = Modifier.size(size * BADGE_BUTTON_ICON_RATIO))
    }
}

private val BADGE_BUTTON_SIZE = 36.dp
private const val BADGE_BUTTON_ICON_RATIO = 20f / 36f
