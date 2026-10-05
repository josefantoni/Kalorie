package antoni.kalorie.features.mycreatedmeal

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import antoni.kalorie.R
import antoni.kalorie.components.BarcodeIcon
import antoni.kalorie.components.BarcodeScannerOverlay
import antoni.kalorie.components.FloatingLabelTextField
import antoni.kalorie.components.FoodItemRow
import antoni.kalorie.components.FoodPortionsSection
import antoni.kalorie.components.NumericRowTextField
import antoni.kalorie.components.SectionCard
import antoni.kalorie.components.SectionCardDivider
import antoni.kalorie.components.rememberScannerAccess
import antoni.kalorie.components.sanitizedGramsText
import antoni.kalorie.core.extensions.KeyboardDoneContainer
import antoni.kalorie.core.models.displayName
import antoni.kalorie.core.utils.AlertItem
import antoni.kalorie.core.utils.isLoading
import antoni.kalorie.features.dashboard.SwipeToDeleteRow
import kotlinx.coroutines.launch
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyCreatedMealEditorView(
    viewModel: MyCreatedMealEditorViewModel,
    onDismiss: () -> Unit,
    navigationIcon: @Composable () -> Unit,
    header: @Composable () -> Unit = {},
) {
    // MARK: - Properties

    val state by viewModel.state.collectAsState()
    val name by viewModel.name.collectAsState()
    val ingredients by viewModel.ingredients.collectAsState()
    val portions by viewModel.portions.collectAsState()
    val searchText by viewModel.searchText.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val externalSearchResults by viewModel.externalSearchResults.collectAsState()
    val isExternalSearchLoading by viewModel.isExternalSearchLoading.collectAsState()
    val isScannerVisible by viewModel.isScannerVisible.collectAsState()
    val lastScannedBarcode by viewModel.lastScannedBarcode.collectAsState()
    val isBarcodeSearchLoading by viewModel.isBarcodeSearchLoading.collectAsState()
    val scannedIngredientId by viewModel.scannedIngredientId.collectAsState()
    val alertItem by viewModel.alertItem.collectAsState()
    val isSaveConfirmationVisible by viewModel.isSaveConfirmationVisible.collectAsState()
    val shouldDismiss by viewModel.shouldDismiss.collectAsState()
    val canSave = remember(name, ingredients, portions) { viewModel.canSave }
    var focusedIngredientId by remember { mutableStateOf<UUID?>(null) }
    val scope = rememberCoroutineScope()
    val scannerAccess = rememberScannerAccess(
        onGranted = viewModel::onScannerButtonTapped,
        onDenied = { viewModel.alertItem.value = AlertItem(titleRes = R.string.addFood_camera_permissionAlert) },
    )

    LaunchedEffect(Unit) { viewModel.onAppear() }

    LaunchedEffect(searchText) { viewModel.onSearchTextChanged() }

    LaunchedEffect(lastScannedBarcode) {
        if (lastScannedBarcode.isNotEmpty()) viewModel.onBarcodeScanned()
    }

    LaunchedEffect(scannedIngredientId) {
        scannedIngredientId?.let { focusedIngredientId = it }
    }

    LaunchedEffect(shouldDismiss) { if (shouldDismiss) onDismiss() }

    // MARK: - Body

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(viewModel.titleRes)) },
                navigationIcon = navigationIcon,
                actions = {
                    TextButton(enabled = canSave && !state.isLoading, onClick = viewModel::onSaveTapped) {
                        Text(stringResource(R.string.myCreatedMeal_button_save))
                    }
                },
            )
        },
    ) { innerPadding ->
        KeyboardDoneContainer(modifier = Modifier.fillMaxSize().padding(innerPadding).imePadding()) {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                item { header() }
                item {
                    SectionCard(modifier = Modifier.padding(top = 8.dp)) {
                        FloatingLabelTextField(
                            title = stringResource(R.string.myCreatedMeal_field_namePlaceholder),
                            text = name,
                            onTextChange = { viewModel.name.value = it },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                if (ingredients.isNotEmpty()) {
                    item { SectionHeader(stringResource(R.string.myCreatedMeal_section_ingredients)) }
                    item {
                        SectionCard {
                            ingredients.forEachIndexed { index, draft ->
                                key("ingredient-${draft.id}") {
                                    SwipeToDeleteRow(
                                        onDeleteRequested = {
                                            val deletedIndex = ingredients.indexOfFirst { it.id == draft.id }
                                            if (deletedIndex >= 0) viewModel.onDeleteIngredient(setOf(deletedIndex))
                                        },
                                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                                    ) {
                                        IngredientRow(
                                            draft = draft,
                                            isFocusRequested = focusedIngredientId == draft.id,
                                            onGramsTextChange = { text ->
                                                viewModel.ingredients.value = ingredients.map { if (it.id == draft.id) it.copy(gramsText = text) else it }
                                            },
                                            onGramsFieldDefocused = { viewModel.onGramsFieldDefocused(draft.id) },
                                        )
                                    }
                                    if (index < ingredients.lastIndex) SectionCardDivider()
                                }
                            }
                        }
                    }
                }
                item { SectionHeader(stringResource(R.string.myCreatedMeal_section_catalogue)) }
                item {
                    SectionCard {
                        FloatingLabelTextField(
                            title = stringResource(R.string.myCreatedMeal_search_placeholder, stringResource(viewModel.searchExampleRes)),
                            text = searchText,
                            onTextChange = { viewModel.searchText.value = it },
                            trailingIcon = {
                                IconButton(onClick = scannerAccess.open) {
                                    Icon(BarcodeIcon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                if (searchText.isNotEmpty()) {
                    item {
                        SectionHeader(
                            stringResource(
                                if (searchResults.isNotEmpty()) R.string.addFood_section_searchResults else R.string.addFood_section_externalResults,
                            ),
                        )
                    }
                    item {
                        SectionCard {
                            if (searchResults.isNotEmpty()) {
                                searchResults.forEachIndexed { index, item ->
                                    key("result-${item.id}") {
                                        FoodItemRow(
                                            item = item,
                                            isFavourite = false,
                                            modifier = Modifier.clickable { focusedIngredientId = viewModel.onSelectSearchResult(item) },
                                        )
                                        if (index < searchResults.lastIndex) SectionCardDivider()
                                    }
                                }
                            } else if (isExternalSearchLoading) {
                                Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator()
                                }
                            } else {
                                externalSearchResults.forEachIndexed { index, item ->
                                    key("external-${item.id}") {
                                        Text(
                                            text = item.displayName,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { focusedIngredientId = viewModel.onSelectSearchResult(item) }
                                                .padding(horizontal = 16.dp, vertical = 12.dp),
                                        )
                                        if (index < externalSearchResults.lastIndex) SectionCardDivider()
                                    }
                                }
                            }
                        }
                    }
                }
                item {
                    FoodPortionsSection(portions = portions, onPortionsChange = { viewModel.portions.value = it })
                }
            }
            if (state.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }
    }

    if (isScannerVisible) {
        Dialog(
            onDismissRequest = { viewModel.isScannerVisible.value = false },
            properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false),
        ) {
            BarcodeScannerOverlay(
                onScannedCode = { viewModel.lastScannedBarcode.value = it },
                isSearching = isBarcodeSearchLoading,
            ) {
                viewModel.isScannerVisible.value = false
            }
        }
    }

    alertItem?.let { alert ->
        AlertDialog(
            onDismissRequest = { viewModel.alertItem.value = null },
            title = { Text(stringResource(alert.titleRes)) },
            text = alert.messageRes?.let { messageRes -> { Text(stringResource(messageRes)) } },
            confirmButton = {
                TextButton(onClick = { viewModel.alertItem.value = null }) {
                    Text(stringResource(R.string.common_ok))
                }
            },
        )
    }

    if (isSaveConfirmationVisible) {
        AlertDialog(
            onDismissRequest = { viewModel.isSaveConfirmationVisible.value = false },
            title = { Text(stringResource(viewModel.confirmationTitleRes)) },
            dismissButton = {
                TextButton(onClick = { viewModel.isSaveConfirmationVisible.value = false }) {
                    Text(stringResource(R.string.common_button_no))
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.isSaveConfirmationVisible.value = false
                        scope.launch { viewModel.onSaveConfirmed() }
                    },
                ) {
                    Text(stringResource(R.string.common_button_yes))
                }
            },
        )
    }
}

// MARK: - Functions

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun IngredientRow(
    draft: MyCreatedMealIngredientDraft,
    isFocusRequested: Boolean,
    onGramsTextChange: (String) -> Unit,
    onGramsFieldDefocused: () -> Unit,
) {
    val focusRequester = remember(draft.id) { FocusRequester() }
    var hadFocus by remember(draft.id) { mutableStateOf(false) }

    LaunchedEffect(isFocusRequested) {
        if (isFocusRequested) focusRequester.requestFocus()
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = draft.item.displayName, modifier = Modifier.weight(1f))
        NumericRowTextField(
            value = draft.gramsText,
            onValueChange = { onGramsTextChange(sanitizedGramsText(it)) },
            placeholder = "100",
            modifier = Modifier
                .width(88.dp)
                .focusRequester(focusRequester)
                .onFocusChanged { focusState ->
                    if (hadFocus && !focusState.isFocused) onGramsFieldDefocused()
                    hadFocus = focusState.isFocused
                },
        )
        Text(text = "g", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp))
    }
}
