package antoni.kalorie.components

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

// MARK: - Body

@Composable
fun AddButton(
    onClick: () -> Unit,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    FilledIconButton(onClick = onClick, enabled = enabled, modifier = modifier.size(ADD_BUTTON_SIZE)) {
        Icon(Icons.Filled.Add, contentDescription = contentDescription)
    }
}

private val ADD_BUTTON_SIZE = 36.dp
