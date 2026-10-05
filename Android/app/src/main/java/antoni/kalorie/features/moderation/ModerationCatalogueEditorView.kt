package antoni.kalorie.features.moderation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import antoni.kalorie.R
import antoni.kalorie.components.BarcodeIcon
import antoni.kalorie.components.FloatingLabelTextField
import antoni.kalorie.components.FoodItemFormBarcodeRow
import antoni.kalorie.components.FoodItemFormSections
import antoni.kalorie.components.SaveToolbarButton
import antoni.kalorie.components.SectionCard
import antoni.kalorie.components.SectionCardDivider
import antoni.kalorie.components.rememberScannerAccess
import antoni.kalorie.core.extensions.KeyboardDoneContainer
import antoni.kalorie.core.models.FoodItemReportDomain
import antoni.kalorie.core.utils.AlertItem
import antoni.kalorie.core.utils.isLoading
import antoni.kalorie.features.addfoodsheet.NutritionLabelCameraView
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModerationCatalogueEditorView(
    viewModel: ModerationCatalogueEditorViewModel,
    onDismiss: () -> Unit,
    reports: List<FoodItemReportDomain> = emptyList(),
) {
    // MARK: - Properties

    val barcodeQuery by viewModel.barcodeQuery.collectAsState()
    val loadedItem by viewModel.loadedItem.collectAsState()
    val formInput by viewModel.formInput.collectAsState()
    val state by viewModel.state.collectAsState()
    val alertItem by viewModel.alertItem.collectAsState()
    val showCheckmark by viewModel.showCheckmark.collectAsState()
    val recognizedFields by viewModel.recognizedFields.collectAsState()
    val isNutritionLabelCameraVisible by viewModel.isNutritionLabelCameraVisible.collectAsState()
    val nutritionLabelCameraHintRes by viewModel.nutritionLabelCameraHintRes.collectAsState()
    val scope = rememberCoroutineScope()
    val scannerAccess = rememberScannerAccess(
        onGranted = viewModel::onNutritionLabelCameraTapped,
        onDenied = { viewModel.alertItem.value = AlertItem(titleRes = R.string.addFood_camera_permissionAlert) },
    )

    LaunchedEffect(Unit) { viewModel.onAppear() }

    // MARK: - Body

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false),
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.moderation_editor_title)) },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                        }
                    },
                    actions = {
                        SaveToolbarButton(
                            title = stringResource(R.string.moderation_button_save),
                            showCheckmark = showCheckmark,
                            isEnabled = loadedItem != null && !state.isLoading,
                        ) {
                            scope.launch { viewModel.onSaveTapped() }
                        }
                    },
                )
            },
        ) { innerPadding ->
            KeyboardDoneContainer(modifier = Modifier.fillMaxSize().padding(innerPadding).consumeWindowInsets(innerPadding).imePadding()) {
                Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    if (reports.isNotEmpty()) {
                        Text(
                            text = stringResource(R.string.moderation_reports_title),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 8.dp),
                        )
                        SectionCard {
                            reports.forEachIndexed { index, report ->
                                Text(
                                    text = report.reason,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                                )
                                if (index < reports.lastIndex) SectionCardDivider()
                            }
                        }
                    }
                    if (!viewModel.isOpenedFromReport) {
                        SectionCard(modifier = Modifier.padding(top = 8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(end = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                FloatingLabelTextField(
                                    title = stringResource(R.string.moderation_editor_searchPlaceholder),
                                    text = barcodeQuery,
                                    onTextChange = { viewModel.barcodeQuery.value = it },
                                    keyboardType = KeyboardType.Number,
                                    modifier = Modifier.weight(1f),
                                )
                                IconButton(onClick = { scope.launch { viewModel.onSearchTapped() } }) {
                                    Icon(BarcodeIcon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                    if (loadedItem != null) {
                        FoodItemFormSections(
                            formInput = formInput,
                            onFormInputChange = { viewModel.formInput.value = it },
                            highlightedFields = recognizedFields,
                            barcodeRow = if (viewModel.isOpenedFromReport) FoodItemFormBarcodeRow.Locked else FoodItemFormBarcodeRow.Hidden,
                            showsAlcoholByVolumeField = true,
                            onNutritionLabelScanTapped = if (viewModel.isOpenedFromReport) null else scannerAccess.open,
                            onFieldEdited = viewModel::onFormFieldEdited,
                        )
                    }
                }
                if (state.isLoading) {
                    Box(modifier = Modifier.matchParentSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            }
        }
    }

    if (isNutritionLabelCameraVisible) {
        Dialog(
            onDismissRequest = { viewModel.isNutritionLabelCameraVisible.value = false },
            properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false),
        ) {
            NutritionLabelCameraView(
                hint = nutritionLabelCameraHintRes?.let { stringResource(it) },
                onRecognized = viewModel::onNutritionLabelRecognized,
                onClose = { viewModel.isNutritionLabelCameraVisible.value = false },
            )
        }
    }

    alertItem?.let { item ->
        AlertDialog(
            onDismissRequest = { viewModel.alertItem.value = null },
            title = { Text(stringResource(item.titleRes)) },
            text = item.messageRes?.let { messageRes -> { Text(stringResource(messageRes)) } },
            confirmButton = {
                TextButton(onClick = { viewModel.alertItem.value = null }) {
                    Text(stringResource(R.string.common_ok))
                }
            },
        )
    }
}
