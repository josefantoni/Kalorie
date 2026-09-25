package antoni.kalorie.features.addfoodsheet

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.TextButton
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import antoni.kalorie.components.BarcodeIcon
import antoni.kalorie.components.BarcodeScannerOverlay
import antoni.kalorie.components.rememberScannerAccess
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import antoni.kalorie.R
import antoni.kalorie.components.FoodItemRow
import antoni.kalorie.core.models.FoodItemDomain
import antoni.kalorie.core.models.FoodItemSubmissionStatus
import antoni.kalorie.core.models.MyCreatedMealDomain
import antoni.kalorie.core.models.displayName
import antoni.kalorie.core.utils.AlertItem
import antoni.kalorie.core.utils.isLoading
import antoni.kalorie.features.dashboard.SwipeToDeleteRow
import antoni.kalorie.features.foodquantity.MealActions
import kotlinx.coroutines.launch

@Composable
fun AddFoodSheetView(
    viewModel: AddFoodSheetViewModel,
    onDismiss: () -> Unit,
    makeFoodQuantityView: @Composable (
        item: FoodItemDomain,
        isFavourite: Boolean,
        meal: MyCreatedMealDomain?,
        onSaved: () -> Unit,
        onFavouriteChanged: (String, Boolean) -> Unit,
        onMealUpdated: (MyCreatedMealDomain) -> Unit,
        mealActions: MealActions?,
        onBack: () -> Unit,
    ) -> Unit,
    makeMealEditorView: @Composable (
        onSaved: () -> Unit,
        onDismiss: () -> Unit,
        navigationIcon: @Composable () -> Unit,
        header: @Composable () -> Unit,
    ) -> Unit,
    makeEditMealView: @Composable (
        meal: MyCreatedMealDomain,
        onSaved: () -> Unit,
        onDismiss: () -> Unit,
        navigationIcon: @Composable () -> Unit,
    ) -> Unit,
) {

    // MARK: - Properties

    val isPushedToQuantityView by viewModel.isPushedToQuantityView.collectAsState()
    val selectedFoodItem by viewModel.selectedFoodItem.collectAsState()
    val shouldDismiss by viewModel.shouldDismiss.collectAsState()
    val scope = rememberCoroutineScope()
    val isReviewPushed by viewModel.isReviewPushed.collectAsState()
    val state by viewModel.state.collectAsState()
    val alertItem by viewModel.alertItem.collectAsState()
    val isNutritionLabelCameraVisible by viewModel.isNutritionLabelCameraVisible.collectAsState()
    val isRecognizingNutritionLabel by viewModel.isRecognizingNutritionLabel.collectAsState()
    val nutritionLabelCameraHintRes by viewModel.nutritionLabelCameraHintRes.collectAsState()
    val backStack = remember(isPushedToQuantityView, selectedFoodItem, isReviewPushed) {
        buildList<AddFoodSheetDestination> {
            add(AddFoodSheetDestination.Search)
            if (isReviewPushed) add(AddFoodSheetDestination.Review)
            selectedFoodItem?.takeIf { isPushedToQuantityView }?.let { add(AddFoodSheetDestination.FoodQuantity(it)) }
        }
    }

    LaunchedEffect(shouldDismiss) { if (shouldDismiss) onDismiss() }

    LaunchedEffect(isNutritionLabelCameraVisible) { if (!isNutritionLabelCameraVisible) viewModel.onNutritionLabelCameraDismissed() }

    // MARK: - Body

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
        NavDisplay(
            backStack = backStack,
            onBack = {
                when {
                    isPushedToQuantityView -> viewModel.isPushedToQuantityView.value = false
                    isReviewPushed -> viewModel.isReviewPushed.value = false
                    else -> onDismiss()
                }
            },
            entryDecorators = listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator(),
            ),
            entryProvider = { destination ->
                when (destination) {
                    is AddFoodSheetDestination.Search -> NavEntry(destination) {
                        val mode by viewModel.mode.collectAsState()
                        when (mode) {
                            AddFoodSheetMode.SEARCH -> SearchContent(viewModel = viewModel, onDismiss = onDismiss)
                            AddFoodSheetMode.NEW_ITEM -> NewItemPromptContent(
                                viewModel = viewModel,
                                onDismiss = onDismiss,
                                modePicker = { ModePicker(viewModel) },
                            )
                            AddFoodSheetMode.CREATE_MEAL -> makeMealEditorView(
                                { scope.launch { viewModel.onMyCreatedMealSaved() } },
                                onDismiss,
                                { CloseButton(onDismiss) },
                                { ModePicker(viewModel) },
                            )
                        }
                    }
                    is AddFoodSheetDestination.Review -> NavEntry(destination) {
                        NewItemReviewContent(viewModel = viewModel, onBack = { viewModel.isReviewPushed.value = false })
                    }
                    is AddFoodSheetDestination.FoodQuantity -> NavEntry(destination) {
                        val meal = viewModel.myCreatedMeal(destination.item)
                        makeFoodQuantityView(
                            destination.item,
                            viewModel.isFavourite(destination.item),
                            meal,
                            viewModel::onFoodConsumedSaved,
                            { id, isFavourite -> viewModel.onFavouriteChanged(id, isFavourite, destination.item) },
                            viewModel::onMyCreatedMealUpdated,
                            meal?.let {
                                MealActions(
                                    makeEditorView = { onBack ->
                                        makeEditMealView(
                                            it,
                                            { scope.launch { viewModel.onMyCreatedMealSaved() } },
                                            onBack,
                                        ) { BackButton(onBack) }
                                    },
                                    onDelete = { scope.launch { viewModel.onDeleteMealConfirmed(it) } },
                                )
                            },
                            { viewModel.isPushedToQuantityView.value = false },
                        )
                    }
                }
            },
        )
        if (state.isLoading) {
            Box(modifier = Modifier.fillMaxSize().clickable(enabled = false) {}, contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
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
                isRecognizing = isRecognizingNutritionLabel,
                hint = nutritionLabelCameraHintRes?.let { stringResource(it) },
                onCaptured = viewModel::onNutritionLabelCaptured,
                onClose = { viewModel.isNutritionLabelCameraVisible.value = false },
            )
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchContent(viewModel: AddFoodSheetViewModel, onDismiss: () -> Unit) {

    // MARK: - Properties

    val searchText by viewModel.searchText.collectAsState()
    val localFoodItems by viewModel.localFoodItems.collectAsState()
    val favouriteFoods by viewModel.favouriteFoods.collectAsState()
    val favouriteIds by viewModel.favouriteIds.collectAsState()
    val scope = rememberCoroutineScope()
    val externalFoodItems by viewModel.externalFoodItems.collectAsState()
    val isExternalSearchLoading by viewModel.isExternalSearchLoading.collectAsState()
    val displayedResults = remember(localFoodItems, favouriteFoods, searchText) { viewModel.displayedResults }
    val isScannerVisible by viewModel.isScannerVisible.collectAsState()
    val lastScannedBarcode by viewModel.lastScannedBarcode.collectAsState()
    val isBarcodeSearchLoading by viewModel.isBarcodeSearchLoading.collectAsState()
    val myCreatedMeals by viewModel.myCreatedMeals.collectAsState()
    val mySubmissions by viewModel.mySubmissions.collectAsState()
    val isSubmissionDeleteConfirmationVisible by viewModel.isSubmissionDeleteConfirmationVisible.collectAsState()
    val isMealDeleteConfirmationVisible by viewModel.isMealDeleteConfirmationVisible.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current
    val scannerAccess = rememberScannerAccess(
        onGranted = viewModel::onScannerButtonTapped,
        onDenied = { viewModel.alertItem.value = AlertItem(titleRes = R.string.addFood_camera_permissionAlert) },
    )

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.onScenePhaseActive(scannerAccess.isAvailable())
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(lastScannedBarcode) {
        if (lastScannedBarcode.isNotEmpty()) viewModel.onBarcodeScanned()
    }

    LaunchedEffect(Unit) { viewModel.onAppear() }

    LaunchedEffect(searchText) { viewModel.onSearchTextChanged() }

    // MARK: - Body

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.addFood_navigationTitle_search)) },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = null)
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(innerPadding).imePadding()) {
            item { ModePicker(viewModel) }
            item {
                TextField(
                    value = searchText,
                    onValueChange = { viewModel.searchText.value = it },
                    placeholder = {
                        Text(
                            stringResource(
                                R.string.addFood_search_placeholder,
                                stringResource(viewModel.searchExampleRes),
                            ),
                        )
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
            if (searchText.isEmpty() && favouriteFoods.isNotEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.addFood_section_favourites),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                items(favouriteFoods, key = { "favourite-${it.id}" }) { item ->
                    FoodItemRow(
                        item = item,
                        isFavourite = true,
                        modifier = Modifier.clickable { scope.launch { viewModel.onSelectFavouriteFood(item) } },
                    )
                }
            }
            if (searchText.isEmpty() && myCreatedMeals.isNotEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.addFood_section_myCreatedMeals),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                items(myCreatedMeals, key = { "meal-${it.id}" }) { meal ->
                    SwipeToDeleteRow(onDeleteRequested = { viewModel.onDeleteMealRequested(meal) }) {
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { viewModel.onSelectFoodItem(meal.asFoodItem()) },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            FoodItemRow(item = meal.asFoodItem(), isFavourite = false, modifier = Modifier.weight(1f))
                            Icon(
                                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(end = 12.dp),
                            )
                        }
                    }
                }
            }
            if (searchText.isEmpty() && mySubmissions.isNotEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.addFood_section_mySubmissions),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                items(mySubmissions, key = { "submission-${it.id}" }) { submission ->
                    SwipeToDeleteRow(onDeleteRequested = { viewModel.onDeleteSubmissionRequested(submission) }) {
                        FoodItemRow(
                            item = submission.item,
                            isFavourite = false,
                            submissionStatus = submission.status,
                            modifier = Modifier.clickable { viewModel.onSelectSubmission(submission) },
                        )
                    }
                }
            }
            if (displayedResults.isNotEmpty() || searchText.isNotEmpty()) {
                item {
                    Text(
                        text = stringResource(
                            if (displayedResults.isNotEmpty()) R.string.addFood_section_searchResults else R.string.addFood_section_externalResults,
                        ),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                if (displayedResults.isNotEmpty()) {
                    items(displayedResults, key = { it.id }) { item ->
                        FoodItemRow(
                            item = item,
                            isFavourite = item.id in favouriteIds,
                            submissionStatus = viewModel.submissionStatus(item),
                            modifier = Modifier.clickable {
                                if (viewModel.submissionStatus(item) == FoodItemSubmissionStatus.REJECTED) {
                                    viewModel.onSelectRejectedSubmission(item)
                                } else {
                                    viewModel.onSelectFoodItem(item)
                                }
                            },
                        )
                    }
                } else if (isExternalSearchLoading) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                } else {
                    items(externalFoodItems, key = { it.id }) { item ->
                        Text(
                            text = item.displayName,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.onSelectFoodItem(item) }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                        )
                    }
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

    if (isSubmissionDeleteConfirmationVisible) {
        AlertDialog(
            onDismissRequest = { viewModel.isSubmissionDeleteConfirmationVisible.value = false },
            title = { Text(stringResource(R.string.addFood_confirm_withdrawSubmission)) },
            dismissButton = {
                TextButton(onClick = { viewModel.isSubmissionDeleteConfirmationVisible.value = false }) {
                    Text(stringResource(R.string.common_button_no))
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.isSubmissionDeleteConfirmationVisible.value = false
                        scope.launch { viewModel.onDeleteSubmissionConfirmed() }
                    },
                ) {
                    Text(stringResource(R.string.common_button_yes))
                }
            },
        )
    }

    if (isMealDeleteConfirmationVisible) {
        AlertDialog(
            onDismissRequest = { viewModel.isMealDeleteConfirmationVisible.value = false },
            title = { Text(stringResource(R.string.myCreatedMeal_confirm_delete)) },
            dismissButton = {
                TextButton(onClick = { viewModel.isMealDeleteConfirmationVisible.value = false }) {
                    Text(stringResource(R.string.common_button_no))
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.isMealDeleteConfirmationVisible.value = false
                        scope.launch { viewModel.onDeleteMealConfirmed() }
                    },
                ) {
                    Text(stringResource(R.string.common_button_yes))
                }
            },
        )
    }
}

@Composable
private fun CloseButton(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(Icons.Filled.Close, contentDescription = null)
    }
}

@Composable
private fun BackButton(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModePicker(viewModel: AddFoodSheetViewModel) {
    val mode by viewModel.mode.collectAsState()
    val modes = AddFoodSheetMode.entries
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        modes.forEachIndexed { index, entry ->
            SegmentedButton(
                selected = mode == entry,
                onClick = { viewModel.onModeSelected(entry) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = modes.size),
            ) {
                Text(stringResource(entry.titleRes))
            }
        }
    }
}
