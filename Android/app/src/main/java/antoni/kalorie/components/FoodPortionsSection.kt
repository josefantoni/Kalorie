package antoni.kalorie.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import antoni.kalorie.R
import antoni.kalorie.core.models.FoodMeasure
import java.util.UUID

@Composable
fun FoodPortionsSection(
    portions: List<FoodPortionDraft>,
    onPortionsChange: (List<FoodPortionDraft>) -> Unit,
    modifier: Modifier = Modifier,
    measure: FoodMeasure = FoodMeasure.GRAMS,
) {

    // MARK: - Properties

    var focusedPortionId by remember { mutableStateOf<UUID?>(null) }

    // MARK: - Body

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.foodPortion_section_title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        PortionDraftListView(
            drafts = portions,
            onDraftsChange = onPortionsChange,
            focusedDraftId = focusedPortionId,
            onDelete = { draft -> onPortionsChange(portions.filter { it.id != draft.id }) },
            measure = measure,
        )
        PortionDraftAddSection(
            drafts = portions,
            onAdd = { draft ->
                onPortionsChange(portions + draft)
                focusedPortionId = draft.id
            },
            modifier = Modifier.padding(vertical = 8.dp),
        )
    }
}
