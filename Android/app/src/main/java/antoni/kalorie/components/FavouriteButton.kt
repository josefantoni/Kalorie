package antoni.kalorie.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import antoni.kalorie.R
import kotlinx.coroutines.delay

@Composable
fun FavouriteButton(isFavourite: Boolean, isEnabled: Boolean = true, onClick: () -> Unit) {

    // MARK: - Properties

    var isLabelVisible by remember { mutableStateOf(false) }
    var wasTapped by remember { mutableStateOf(false) }
    var previousIsFavourite by remember { mutableStateOf(isFavourite) }
    val showsLabel = isFavourite && isLabelVisible
    val tint = MaterialTheme.colorScheme.error

    LaunchedEffect(isFavourite) {
        if (isFavourite == previousIsFavourite) return@LaunchedEffect
        previousIsFavourite = isFavourite
        isLabelVisible = isFavourite && wasTapped
        wasTapped = false
        if (isLabelVisible) {
            delay(LABEL_DURATION_MILLIS)
            isLabelVisible = false
        }
    }

    // MARK: - Body

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(if (showsLabel) tint else Color.Transparent)
            .clickable(enabled = isEnabled, role = Role.Button) {
                wasTapped = true
                onClick()
            }
            .animateContentSize()
            .padding(horizontal = if (showsLabel) 16.dp else 10.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showsLabel) {
            Text(text = stringResource(R.string.common_button_favourite), color = MaterialTheme.colorScheme.onError)
        }
        Icon(
            imageVector = if (isFavourite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
            contentDescription = stringResource(R.string.common_button_favourite),
            tint = if (showsLabel) MaterialTheme.colorScheme.onError else tint,
        )
    }
}

private const val LABEL_DURATION_MILLIS = 1_000L
