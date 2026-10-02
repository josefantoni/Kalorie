package antoni.kalorie.components

import androidx.compose.animation.Crossfade
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

@Composable
fun SaveToolbarButton(title: String, showCheckmark: Boolean, isEnabled: Boolean, onClick: () -> Unit) {
    // MARK: - Body

    TextButton(onClick = onClick, enabled = isEnabled) {
        Crossfade(targetState = showCheckmark, label = "saveCheckmark") { isCheckmarkVisible ->
            if (isCheckmarkVisible) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = AppColors.success)
            } else {
                Text(title)
            }
        }
    }
}
