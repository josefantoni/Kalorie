package antoni.kalorie.components

import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

// MARK: - Body

@Composable
fun BadgeButton(
    icon: ImageVector,
    onClick: () -> Unit,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    FilledIconButton(onClick = onClick, enabled = enabled, modifier = modifier.size(BADGE_BUTTON_SIZE)) {
        Icon(icon, contentDescription = contentDescription, modifier = Modifier.size(BADGE_BUTTON_ICON_SIZE))
    }
}

private val BADGE_BUTTON_SIZE = 36.dp
private val BADGE_BUTTON_ICON_SIZE = 20.dp
