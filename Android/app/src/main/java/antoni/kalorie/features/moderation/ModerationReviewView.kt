package antoni.kalorie.features.moderation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import antoni.kalorie.R
import antoni.kalorie.components.FoodItemFormBarcodeRow
import antoni.kalorie.components.FoodItemFormSections
import antoni.kalorie.components.rememberScannerAccess
import antoni.kalorie.core.utils.AlertItem
import antoni.kalorie.features.addfoodsheet.NutritionLabelCameraView
import antoni.kalorie.core.models.displayName
import antoni.kalorie.core.utils.isLoading
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModerationReviewView(viewModel: ModerationReviewViewModel, onDismiss: () -> Unit) {

    // MARK: - Properties

    val formInput by viewModel.formInput.collectAsState()
    val state by viewModel.state.collectAsState()
    val alertItem by viewModel.alertItem.collectAsState()
    val isRejectSheetVisible by viewModel.isRejectSheetVisible.collectAsState()
    val rejectReason by viewModel.rejectReason.collectAsState()
    val shouldDismiss by viewModel.shouldDismiss.collectAsState()
    val similarCatalogueItems by viewModel.similarCatalogueItems.collectAsState()
    val isSimilarSectionAvailable by viewModel.isSimilarCatalogueItemsSectionAvailable.collectAsState()
    val recognizedFields by viewModel.recognizedFields.collectAsState()
    val isNutritionLabelCameraVisible by viewModel.isNutritionLabelCameraVisible.collectAsState()
    val isRecognizingNutritionLabel by viewModel.isRecognizingNutritionLabel.collectAsState()
    val nutritionLabelCameraHintRes by viewModel.nutritionLabelCameraHintRes.collectAsState()
    val scope = rememberCoroutineScope()
    val scannerAccess = rememberScannerAccess(
        onGranted = viewModel::onNutritionLabelCameraTapped,
        onDenied = { viewModel.alertItem.value = AlertItem(titleRes = R.string.addFood_camera_permissionAlert) },
    )

    LaunchedEffect(Unit) { viewModel.onAppear() }

    LaunchedEffect(shouldDismiss, alertItem) { if (shouldDismiss && alertItem == null) onDismiss() }

    // MARK: - Body

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false),
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(formInput.name) },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                        }
                    },
                )
            },
            bottomBar = {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Button(
                        onClick = { scope.launch { viewModel.onApproveTapped() } },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.moderation_button_approve))
                    }
                    Button(
                        onClick = { viewModel.isRejectSheetVisible.value = true },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    ) {
                        Text(stringResource(R.string.moderation_button_reject))
                    }
                }
            },
        ) { innerPadding ->
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding).imePadding()) {
                Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    viewModel.rejectReasonIfAny?.let { reason ->
                        Text(
                            text = stringResource(R.string.addFood_submission_rejectedReason, reason),
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                    FoodItemFormSections(
                        formInput = formInput,
                        onFormInputChange = { viewModel.formInput.value = it },
                        barcodeRow = FoodItemFormBarcodeRow.Locked,
                        highlightedFields = recognizedFields,
                        onNutritionLabelScanTapped = scannerAccess.open,
                        onFieldEdited = viewModel::onFormFieldEdited,
                    )
                    if (viewModel.showsSimilarCatalogueItemsSection && isSimilarSectionAvailable) {
                        Text(
                            text = stringResource(R.string.moderation_similarItems_title),
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(start = 16.dp, top = 24.dp, end = 16.dp, bottom = 8.dp),
                        )
                        if (similarCatalogueItems.isEmpty()) {
                            Text(
                                text = stringResource(R.string.moderation_similarItems_empty),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp),
                            )
                        } else {
                            similarCatalogueItems.forEach { item ->
                                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
                                    Text(item.displayName)
                                    Text(
                                        text = "${item.caloriesPerHundredGrams.roundToInt()} kcal / 100 ${stringResource(item.measure.unitSymbolRes)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
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

    if (isRejectSheetVisible) {
        AlertDialog(
            onDismissRequest = { viewModel.isRejectSheetVisible.value = false },
            title = { Text(stringResource(R.string.moderation_button_reject)) },
            text = {
                TextField(
                    value = rejectReason,
                    onValueChange = { viewModel.rejectReason.value = it },
                    placeholder = { Text(stringResource(R.string.moderation_reject_reasonPlaceholder)) },
                )
            },
            dismissButton = {
                TextButton(onClick = { viewModel.isRejectSheetVisible.value = false }) {
                    Text(stringResource(R.string.common_button_cancel))
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.isRejectSheetVisible.value = false
                        scope.launch { viewModel.onRejectConfirmed() }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Text(stringResource(R.string.moderation_button_reject))
                }
            },
        )
    }

    if (isNutritionLabelCameraVisible) {
        Dialog(
            onDismissRequest = { viewModel.isNutritionLabelCameraVisible.value = false },
            properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false),
        ) {
            NutritionLabelCameraView(
                isRecognizing = isRecognizingNutritionLabel,
                hint = nutritionLabelCameraHintRes?.let { stringResource(it) },
                onCaptured = viewModel::onNutritionLabelCaptured,
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
