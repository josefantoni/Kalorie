package antoni.kalorie.features.addfoodsheet

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
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
import antoni.kalorie.core.models.displayName
import kotlinx.coroutines.launch

@Composable
fun AddFoodSheetView(
    viewModel: AddFoodSheetViewModel,
    onDismiss: () -> Unit,
    makeFoodQuantityView: @Composable (
        item: FoodItemDomain,
        isFavourite: Boolean,
        onSaved: () -> Unit,
        onFavouriteChanged: (String, Boolean) -> Unit,
        onBack: () -> Unit,
    ) -> Unit,
) {

    // MARK: - Properties

    val isPushedToQuantityView by viewModel.isPushedToQuantityView.collectAsState()
    val selectedFoodItem by viewModel.selectedFoodItem.collectAsState()
    val shouldDismiss by viewModel.shouldDismiss.collectAsState()
    val backStack = remember(isPushedToQuantityView, selectedFoodItem) {
        buildList<AddFoodSheetDestination> {
            add(AddFoodSheetDestination.Search)
            selectedFoodItem?.takeIf { isPushedToQuantityView }?.let { add(AddFoodSheetDestination.FoodQuantity(it)) }
        }
    }

    LaunchedEffect(shouldDismiss) { if (shouldDismiss) onDismiss() }

    // MARK: - Body

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false),
    ) {
        NavDisplay(
            backStack = backStack,
            onBack = { if (isPushedToQuantityView) viewModel.isPushedToQuantityView.value = false else onDismiss() },
            entryDecorators = listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator(),
            ),
            entryProvider = { destination ->
                when (destination) {
                    is AddFoodSheetDestination.Search -> NavEntry(destination) {
                        SearchContent(viewModel = viewModel, onDismiss = onDismiss)
                    }
                    is AddFoodSheetDestination.FoodQuantity -> NavEntry(destination) {
                        makeFoodQuantityView(
                            destination.item,
                            viewModel.isFavourite(destination.item),
                            viewModel::onFoodConsumedSaved,
                            { id, isFavourite -> viewModel.onFavouriteChanged(id, isFavourite, destination.item) },
                            { viewModel.isPushedToQuantityView.value = false },
                        )
                    }
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
            if (displayedResults.isNotEmpty() || searchText.isNotEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.addFood_section_externalResults),
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
                            modifier = Modifier.clickable { viewModel.onSelectFoodItem(item) },
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
}
