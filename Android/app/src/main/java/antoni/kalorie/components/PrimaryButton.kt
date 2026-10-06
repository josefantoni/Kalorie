package antoni.kalorie.components

import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

enum class PrimaryButtonStyle { PRIMARY, DESTRUCTIVE }

// MARK: - Body

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: PrimaryButtonStyle = PrimaryButtonStyle.PRIMARY,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = when (style) {
                PrimaryButtonStyle.PRIMARY -> AppColors.accent
                PrimaryButtonStyle.DESTRUCTIVE -> AppColors.error
            },
            contentColor = Color.White,
        ),
        modifier = modifier.height(PRIMARY_BUTTON_HEIGHT),
    ) {
        Text(text, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
    }
}

private val PRIMARY_BUTTON_HEIGHT = 56.dp
