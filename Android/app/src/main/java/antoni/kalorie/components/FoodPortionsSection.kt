package antoni.kalorie.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
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

    SectionGroup(title = stringResource(R.string.foodPortion_section_title), modifier = modifier.fillMaxWidth()) {
        SectionCard {
            PortionDraftListView(
                drafts = portions,
                onDraftsChange = onPortionsChange,
                focusedDraftId = focusedPortionId,
                onDelete = { draft -> onPortionsChange(portions.filter { it.id != draft.id }) },
                measure = measure,
            )
        }
        PortionDraftAddSection(
            drafts = portions,
            onAdd = { draft ->
                onPortionsChange(portions + draft)
                focusedPortionId = draft.id
            },
        )
    }
}
