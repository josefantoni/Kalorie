package antoni.kalorie.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import antoni.kalorie.R
import antoni.kalorie.core.models.FoodItemDomain
import antoni.kalorie.core.models.FoodItemSubmissionStatus
import antoni.kalorie.core.models.displayName

@Composable
fun FoodItemRow(
    item: FoodItemDomain,
    isFavourite: Boolean,
    modifier: Modifier = Modifier,
    submissionStatus: FoodItemSubmissionStatus? = null,
) {

    // MARK: - Body

    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(item.displayName)
            if (submissionStatus != null) {
                Text(
                    text = stringResource(
                        when (submissionStatus) {
                            FoodItemSubmissionStatus.PENDING -> R.string.addFood_submission_pending
                            FoodItemSubmissionStatus.REJECTED -> R.string.addFood_submission_rejected
                        },
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (isFavourite) {
            Icon(
                imageVector = Icons.Filled.Favorite,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
            )
        }
    }
}
