package antoni.kalorie.core.usecases

import antoni.kalorie.core.auth.AuthCommandProviderProtocol
import antoni.kalorie.core.auth.AuthError
import antoni.kalorie.core.auth.AuthProviderProtocol
import antoni.kalorie.core.auth.PendingMergeSnapshotStoreProtocol
import antoni.kalorie.core.models.FoodItemReportDomain
import antoni.kalorie.core.networking.FavouriteFoodDTO
import antoni.kalorie.core.networking.FirestoreDataProviderProtocol
import antoni.kalorie.core.networking.FoodConsumedDTO
import antoni.kalorie.core.networking.FoodItemPersonalPortionsDTO
import antoni.kalorie.core.networking.FoodItemReportDTO
import antoni.kalorie.core.networking.FoodItemSubmissionDTO
import antoni.kalorie.core.networking.MealTypeDTO
import antoni.kalorie.core.networking.MyCreatedMealDTO
import antoni.kalorie.core.networking.loadAsync
import antoni.kalorie.core.utils.Constants
import antoni.kalorie.core.utils.Log
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.CancellationException

sealed class DeleteAccountError : Exception() {
    data class RequiresRecentLogin(val dataAlreadyDeleted: Boolean) : DeleteAccountError()
}

interface DeleteAccountUseCaseProtocol {
    suspend operator fun invoke(skipDataWipe: Boolean = false)
}

class DeleteAccountUseCase(
    private val dataProvider: FirestoreDataProviderProtocol,
    private val authProvider: AuthProviderProtocol,
    private val authCommandProvider: AuthCommandProviderProtocol,
    private val snapshotStore: PendingMergeSnapshotStoreProtocol,
) : DeleteAccountUseCaseProtocol {

    // MARK: - Functions

    override suspend fun invoke(skipDataWipe: Boolean) {
        val userId = authProvider.userId ?: throw AuthError.NotAuthenticated
        val lastSignInDate = authProvider.lastSignInDate
        if (lastSignInDate != null && Duration.between(lastSignInDate, Instant.now()).seconds > Constants.Auth.RECENT_LOGIN_THRESHOLD_SECONDS) {
            throw DeleteAccountError.RequiresRecentLogin(dataAlreadyDeleted = false)
        }
        // A snapshot left by a failed merge would otherwise be resumed into the fresh anonymous
        // account that follows the deletion. Idempotent, so a retry with skipDataWipe is safe.
        snapshotStore.delete()
        if (!skipDataWipe) wipeFirestoreData(userId)
        try {
            authCommandProvider.deleteCurrentUser()
        } catch (error: CancellationException) {
            throw error
        } catch (error: FirebaseAuthRecentLoginRequiredException) {
            throw DeleteAccountError.RequiresRecentLogin(dataAlreadyDeleted = true)
        }
    }

    // MARK: - Private

    private suspend fun wipeFirestoreData(userId: String) {
        val mealTypes: List<MealTypeDTO> = dataProvider.loadAsync(from = Constants.Firestore.mealTypes(userId))
        mealTypes.forEach { dataProvider.deleteAsync(id = it.id, from = Constants.Firestore.mealTypes(userId)) }
        val foods: List<FoodConsumedDTO> = dataProvider.loadAsync(from = Constants.Firestore.foodConsumed(userId))
        foods.forEach { dataProvider.deleteAsync(id = it.id, from = Constants.Firestore.foodConsumed(userId)) }
        val favouriteFoods: List<FavouriteFoodDTO> = dataProvider.loadAsync(from = Constants.Firestore.favouriteFoods(userId))
        favouriteFoods.forEach { dataProvider.deleteAsync(id = it.id, from = Constants.Firestore.favouriteFoods(userId)) }
        val myCreatedMeals: List<MyCreatedMealDTO> = dataProvider.loadAsync(from = Constants.Firestore.myCreatedMeals(userId))
        myCreatedMeals.forEach { dataProvider.deleteAsync(id = it.id, from = Constants.Firestore.myCreatedMeals(userId)) }
        val foodItemPortions: List<FoodItemPersonalPortionsDTO> = dataProvider.loadAsync(from = Constants.Firestore.foodItemPortions(userId))
        foodItemPortions.forEach { dataProvider.deleteAsync(id = it.id, from = Constants.Firestore.foodItemPortions(userId)) }
        val submissions: List<FoodItemSubmissionDTO> = dataProvider.loadAsync(
            from = Constants.Firestore.FOOD_ITEM_SUBMISSIONS,
            field = "submitted_by",
            isEqualTo = userId,
            orderBy = "submitted_at",
            descending = false,
        )
        submissions.forEach { dataProvider.deleteAsync(id = it.id, from = Constants.Firestore.FOOD_ITEM_SUBMISSIONS) }
        val reports: List<FoodItemReportDTO> = dataProvider.loadAsync(
            from = Constants.Firestore.FOOD_ITEM_REPORTS,
            field = "reported_by",
            isEqualTo = userId,
            orderBy = "reported_at",
            descending = false,
        )
        reports.forEach {
            dataProvider.deleteAsync(id = FoodItemReportDomain.id(barcode = it.barcode, userId = userId), from = Constants.Firestore.FOOD_ITEM_REPORTS)
        }
        try {
            dataProvider.deleteAsync(id = userId, from = Constants.Firestore.USERS)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.error(error, Constants.LogCategory.ACCOUNT)
        }
    }
}
