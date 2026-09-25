package antoni.kalorie.features.addfoodsheet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import antoni.kalorie.R
import antoni.kalorie.components.BarcodeScannerOverlay
import antoni.kalorie.components.FoodItemFormBarcodeRow
import antoni.kalorie.components.FoodItemFormSections
import antoni.kalorie.components.rememberScannerAccess
import antoni.kalorie.core.utils.AlertItem
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewItemPromptContent(viewModel: AddFoodSheetViewModel, onDismiss: () -> Unit, modePicker: @Composable () -> Unit) {

    // MARK: - Body

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.addFood_navigationTitle_newItem)) },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = null)
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            modePicker()
            Column(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                TextButton(onClick = viewModel::onAddManuallyTapped) {
                    Text(stringResource(R.string.addFood_button_addFoodManually))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewItemReviewContent(viewModel: AddFoodSheetViewModel, onBack: () -> Unit) {

    // MARK: - Properties

    val formInput by viewModel.formInput.collectAsState()
    val rejectionReason by viewModel.rejectionReasonBeingEdited.collectAsState()
    val isSubmissionConfirmationVisible by viewModel.isSubmissionConfirmationVisible.collectAsState()
    val isMissingBarcodeConfirmationVisible by viewModel.isMissingBarcodeConfirmationVisible.collectAsState()
    val isBarcodeRescanVisible by viewModel.isBarcodeRescanVisible.collectAsState()
    val rescannedBarcode by viewModel.rescannedBarcode.collectAsState()
    val scope = rememberCoroutineScope()
    val scannerAccess = rememberScannerAccess(
        onGranted = viewModel::onBarcodeRescanTapped,
        onDenied = { viewModel.alertItem.value = AlertItem(titleRes = R.string.addFood_camera_permissionAlert) },
    )

    LaunchedEffect(rescannedBarcode) { viewModel.onBarcodeRescanned() }

    // MARK: - Body

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.addFood_navigationTitle_newItem)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
        bottomBar = {
            Button(
                onClick = { scope.launch { viewModel.onCreateFoodItem() } },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Text(stringResource(R.string.addFood_button_add))
            }
        },
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding).imePadding()) {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                rejectionReason?.let { reason ->
                    item {
                        Text(
                            text = stringResource(R.string.addFood_submission_rejectedReason, reason),
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
                item {
                    FoodItemFormSections(
                        formInput = formInput,
                        onFormInputChange = { viewModel.formInput.value = it },
                        barcodeRow = if (viewModel.isEditingSubmission) {
                            FoodItemFormBarcodeRow.Locked
                        } else {
                            FoodItemFormBarcodeRow.Editable(onScanTapped = scannerAccess.open)
                        },
                    )
                }
            }
        }
    }

    if (isBarcodeRescanVisible) {
        Dialog(
            onDismissRequest = { viewModel.isBarcodeRescanVisible.value = false },
            properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false),
        ) {
            BarcodeScannerOverlay(
                onScannedCode = { viewModel.rescannedBarcode.value = it },
                isSearching = false,
            ) {
                viewModel.isBarcodeRescanVisible.value = false
            }
        }
    }

    if (isSubmissionConfirmationVisible) {
        AlertDialog(
            onDismissRequest = viewModel::onSubmissionConfirmationDismissed,
            title = { Text(stringResource(R.string.addFood_submission_submitted)) },
            text = { Text(stringResource(R.string.addFood_submission_submittedMessage)) },
            confirmButton = {
                TextButton(onClick = viewModel::onSubmissionConfirmationDismissed) {
                    Text(stringResource(R.string.common_ok))
                }
            },
        )
    }

    if (isMissingBarcodeConfirmationVisible) {
        AlertDialog(
            onDismissRequest = { viewModel.isMissingBarcodeConfirmationVisible.value = false },
            title = { Text(stringResource(R.string.addFood_confirm_missingBarcode)) },
            dismissButton = {
                TextButton(onClick = { viewModel.isMissingBarcodeConfirmationVisible.value = false }) {
                    Text(stringResource(R.string.common_button_no))
                }
            },
            confirmButton = {
                TextButton(onClick = { scope.launch { viewModel.onMissingBarcodeConfirmed() } }) {
                    Text(stringResource(R.string.common_button_yes))
                }
            },
        )
    }
}
