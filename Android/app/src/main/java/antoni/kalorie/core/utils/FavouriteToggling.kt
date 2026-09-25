package antoni.kalorie.core.utils

import antoni.kalorie.R
import antoni.kalorie.core.models.FoodItemDomain
import antoni.kalorie.core.usecases.AddFavouriteFoodUseCaseProtocol
import antoni.kalorie.core.usecases.RemoveFavouriteFoodUseCaseProtocol
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow

sealed class FavouriteToggleError : Exception() {
    data object MissingItem : FavouriteToggleError()
}

interface FavouriteToggling {
    val isFavourite: MutableStateFlow<Boolean>
    val isTogglingFavourite: MutableStateFlow<Boolean>
    val alertItem: MutableStateFlow<AlertItem?>

    // MARK: - Functions

    suspend fun toggleFavourite(
        item: FoodItemDomain?,
        removalId: String,
        addFavouriteFood: AddFavouriteFoodUseCaseProtocol,
        removeFavouriteFood: RemoveFavouriteFoodUseCaseProtocol,
        onToggled: ((Boolean) -> Unit)? = null,
    ) {
        if (isTogglingFavourite.value) return
        isTogglingFavourite.value = true
        val newValue = !isFavourite.value
        isFavourite.value = newValue
        try {
            if (newValue) {
                addFavouriteFood(item ?: throw FavouriteToggleError.MissingItem)
            } else {
                removeFavouriteFood(removalId)
            }
            onToggled?.invoke(newValue)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.error(error, Constants.LogCategory.FAVOURITES)
            isFavourite.value = !newValue
            alertItem.value = AlertItem(titleRes = R.string.addFood_error_favouriteFailed)
        } finally {
            isTogglingFavourite.value = false
        }
    }
}
