package antoni.kalorie.features.foodquantity

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import antoni.kalorie.R
import antoni.kalorie.components.PortionDraftAddSection
import antoni.kalorie.components.PortionDraftListView
import antoni.kalorie.components.SaveToolbarButton
import antoni.kalorie.components.SectionCard
import antoni.kalorie.components.SectionCardDivider
import antoni.kalorie.core.extensions.KeyboardDoneContainer
import antoni.kalorie.core.extensions.formattedAmount
import antoni.kalorie.features.dashboard.SwipeToDeleteRow
import kotlinx.coroutines.launch
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoodPortionsManagerView(viewModel: FoodQuantityViewModel, onBack: () -> Unit) {
    // MARK: - Properties

    val personalPortions by viewModel.personalPortions.collectAsState()
    val portionDrafts by viewModel.portionDrafts.collectAsState()
    val showPortionCheckmark by viewModel.showPortionCheckmark.collectAsState()
    val scope = rememberCoroutineScope()
    var focusedDraftId by remember { mutableStateOf<UUID?>(null) }
    val measure = viewModel.item.measure
    val canSavePortionDrafts = portionDrafts.any { it.isComplete }

    // MARK: - Body

    Dialog(
        onDismissRequest = onBack,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false),
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.myPortions_title)) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                        }
                    },
                    actions = {
                        SaveToolbarButton(
                            title = stringResource(R.string.myPortions_button_save),
                            showCheckmark = showPortionCheckmark,
                            isEnabled = canSavePortionDrafts,
                        ) {
                            focusedDraftId = null
                            scope.launch { viewModel.onSavePersonalPortions() }
                        }
                    },
                )
            },
        ) { innerPadding ->
            KeyboardDoneContainer(modifier = Modifier.fillMaxSize().padding(innerPadding).imePadding()) {
                Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    if (personalPortions.isEmpty()) {
                        Text(
                            text = stringResource(R.string.myPortions_empty),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                    SectionCard {
                        personalPortions.forEachIndexed { index, portion ->
                            SwipeToDeleteRow(
                                onDeleteRequested = { scope.launch { viewModel.onDeletePersonalPortion(portion) } },
                                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(portion.name)
                                    Text(
                                        text = portion.grams.formattedAmount(measure, minFractionDigits = 0),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            if (index < personalPortions.lastIndex || portionDrafts.isNotEmpty()) SectionCardDivider()
                        }
                        PortionDraftListView(
                            drafts = portionDrafts,
                            onDraftsChange = { viewModel.portionDrafts.value = it },
                            focusedDraftId = focusedDraftId,
                            onDelete = viewModel::onDeletePortionDraft,
                            measure = measure,
                        )
                    }
                    PortionDraftAddSection(
                        drafts = portionDrafts,
                        onAdd = { draft ->
                            viewModel.portionDrafts.value = portionDrafts + draft
                            focusedDraftId = draft.id
                        },
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
            }
        }
    }
}
