package antoni.kalorie.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import antoni.kalorie.R
import antoni.kalorie.core.models.FoodMeasure
import antoni.kalorie.features.dashboard.SwipeToDeleteRow
import java.util.UUID

data class FoodPortionDraft(
    val id: UUID = UUID.randomUUID(),
    val name: String,
    val gramsText: String,
) {

    // MARK: - Properties

    val isComplete: Boolean
        get() = name.isNotBlank() && gramsText.isNotEmpty()

    // MARK: - Functions

    companion object {
        val blank: FoodPortionDraft
            get() = FoodPortionDraft(name = "", gramsText = "")
    }
}

@Composable
fun PortionDraftListView(
    drafts: List<FoodPortionDraft>,
    onDraftsChange: (List<FoodPortionDraft>) -> Unit,
    focusedDraftId: UUID?,
    onDelete: (FoodPortionDraft) -> Unit,
    modifier: Modifier = Modifier,
    measure: FoodMeasure = FoodMeasure.GRAMS,
) {

    // MARK: - Body

    Column(modifier = modifier.fillMaxWidth()) {
        for ((index, draft) in drafts.withIndex()) {
            val focusRequester = remember(draft.id) { FocusRequester() }
            LaunchedEffect(focusedDraftId) {
                if (focusedDraftId == draft.id) focusRequester.requestFocus()
            }
            SwipeToDeleteRow(onDeleteRequested = { onDelete(draft) }) {
                Column(modifier = Modifier.padding(top = 12.dp, bottom = 12.dp)) {
                    PortionInputRow(
                        name = draft.name,
                        gramsText = draft.gramsText,
                        measure = measure,
                        onNameChange = { newName -> onDraftsChange(drafts.map { if (it.id == draft.id) it.copy(name = newName) else it }) },
                        onGramsTextChange = { newText -> onDraftsChange(drafts.map { if (it.id == draft.id) it.copy(gramsText = newText) else it }) },
                        focusRequester = focusRequester,
                    )
                }
            }
            if (index < drafts.size - 1) HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
        }
    }
}

@Composable
fun PortionDraftAddSection(drafts: List<FoodPortionDraft>, onAdd: (FoodPortionDraft) -> Unit, modifier: Modifier = Modifier) {

    // MARK: - Body

    Column(modifier = modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        FilledIconButton(onClick = { onAdd(FoodPortionDraft.blank) }, enabled = drafts.all { it.isComplete }) {
            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.foodPortion_button_add))
        }
    }
}
