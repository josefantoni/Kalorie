package antoni.kalorie.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LocalBar
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RichTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import antoni.kalorie.R
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlcoholicDrinkHintView(formattedAlcoholByVolume: String, modifier: Modifier = Modifier) {
    val tooltipState = rememberTooltipState(isPersistent = true)
    val scope = rememberCoroutineScope()

    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { RichTooltip { Text(stringResource(R.string.common_alcoholicDrink_explanation)) } },
        state = tooltipState,
        enableUserInput = false,
        modifier = modifier,
    ) {
        FilledTonalButton(
            onClick = { scope.launch { tooltipState.show() } },
            colors = ButtonDefaults.filledTonalButtonColors(containerColor = AppColors.warning, contentColor = Color.Black),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            modifier = Modifier.heightIn(min = 32.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(Icons.Outlined.LocalBar, contentDescription = null, modifier = Modifier.size(16.dp))
                Text(
                    text = "${stringResource(R.string.common_alcoholicDrink_label)} · $formattedAlcoholByVolume",
                    style = MaterialTheme.typography.labelMedium,
                )
                Icon(Icons.Outlined.Info, contentDescription = null, modifier = Modifier.size(16.dp))
            }
        }
    }
}
