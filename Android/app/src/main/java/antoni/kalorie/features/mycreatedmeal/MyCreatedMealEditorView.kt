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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import antoni.kalorie.R
import antoni.kalorie.components.BarcodeIcon
import antoni.kalorie.components.BarcodeScannerOverlay
import antoni.kalorie.components.FoodItemRow
import antoni.kalorie.components.FoodPortionsSection
import antoni.kalorie.components.rememberScannerAccess
import antoni.kalorie.components.sanitizedGramsText
import antoni.kalorie.core.models.displayName
import antoni.kalorie.core.utils.AlertItem
import antoni.kalorie.core.utils.isLoading
import antoni.kalorie.features.dashboard.SwipeToDeleteRow
import java.util.UUID
import kotlinx.coroutines.launch

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
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding).imePadding()) {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                item { header() }
                item {
                    TextField(
                        value = name,
                        onValueChange = { viewModel.name.value = it },
                        placeholder = { Text(stringResource(R.string.myCreatedMeal_field_namePlaceholder)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                if (ingredients.isNotEmpty()) {
                    item { SectionHeader(stringResource(R.string.myCreatedMeal_section_ingredients)) }
                    items(ingredients, key = { "ingredient-${it.id}" }) { draft ->
                        SwipeToDeleteRow(
                            onDeleteRequested = {
                                val index = ingredients.indexOfFirst { it.id == draft.id }
                                if (index >= 0) viewModel.onDeleteIngredient(setOf(index))
                            },
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
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    }
                }
                item { SectionHeader(stringResource(R.string.myCreatedMeal_section_catalogue)) }
                item {
                    TextField(
                        value = searchText,
                        onValueChange = { viewModel.searchText.value = it },
                        placeholder = {
                            Text(stringResource(R.string.myCreatedMeal_search_placeholder, stringResource(viewModel.searchExampleRes)))
                        },
                        trailingIcon = {
                            IconButton(onClick = scannerAccess.open) {
                                Icon(BarcodeIcon, contentDescription = null)
                            }
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                if (searchText.isNotEmpty()) {
                    item { SectionHeader(stringResource(R.string.addFood_section_externalResults)) }
                    if (searchResults.isNotEmpty()) {
                        items(searchResults, key = { "result-${it.id}" }) { item ->
                            FoodItemRow(
                                item = item,
                                isFavourite = false,
                                modifier = Modifier.clickable { focusedIngredientId = viewModel.onSelectSearchResult(item) },
                            )
                        }
                    } else if (isExternalSearchLoading) {
                        item {
                            Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        }
                    } else {
                        items(externalSearchResults, key = { "external-${it.id}" }) { item ->
                            Text(
                                text = item.displayName,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { focusedIngredientId = viewModel.onSelectSearchResult(item) }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                            )
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
        TextField(
            value = draft.gramsText,
            onValueChange = { onGramsTextChange(sanitizedGramsText(it)) },
            placeholder = { Text("100") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            textStyle = MaterialTheme.typography.bodyLarge.copy(textAlign = TextAlign.End),
            singleLine = true,
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
