package antoni.kalorie.features.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import antoni.kalorie.R
import antoni.kalorie.components.AppColors
import antoni.kalorie.components.SectionCard
import antoni.kalorie.components.tintedSurfaceColor

private val BADGE_SIZE = 36.dp
private val MIN_TARGET = 48.dp

// MARK: - Body

@Composable
fun ReviewPromptCard(
    onRate: () -> Unit,
    onNotNow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SectionCard(
        modifier = modifier,
        color = tintedSurfaceColor(),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    modifier = Modifier.size(BADGE_SIZE).clip(CircleShape).background(AppColors.accent),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Favorite, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                }
                Text(
                    text = stringResource(R.string.reviewPrompt_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onNotNow, modifier = Modifier.size(MIN_TARGET)) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = stringResource(R.string.reviewPrompt_actionNotNow),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            Text(
                text = stringResource(R.string.reviewPrompt_body, stringResource(R.string.reviewPrompt_storeName_googlePlay)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = onRate,
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.accent, contentColor = Color.White),
                modifier = Modifier.align(Alignment.End).defaultMinSize(minHeight = MIN_TARGET),
            ) {
                Text(stringResource(R.string.reviewPrompt_actionRate), fontWeight = FontWeight.Bold)
            }
        }
    }
}
