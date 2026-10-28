package antoni.kalorie.core.usecases

import antoni.kalorie.core.auth.AppleSignInProviderProtocol
import antoni.kalorie.core.auth.AuthCommandProviderProtocol
import antoni.kalorie.core.auth.AuthError
import antoni.kalorie.core.auth.AuthProviderKind
import antoni.kalorie.core.auth.AuthProviderProtocol
import antoni.kalorie.core.auth.PendingMergeSnapshotStoreProtocol
import antoni.kalorie.core.models.FoodItemReportDomain
import antoni.kalorie.core.networking.BodyWeightDTO
import antoni.kalorie.core.networking.FavouriteFoodDTO
import antoni.kalorie.core.networking.FirestoreDataProviderProtocol
import antoni.kalorie.core.networking.FoodConsumedDTO
import antoni.kalorie.core.networking.FoodItemPersonalPortionsDTO
import antoni.kalorie.core.networking.FoodItemReportDTO
import antoni.kalorie.core.networking.FoodItemSubmissionDTO
import antoni.kalorie.core.networking.MealTypeDTO
import antoni.kalorie.core.networking.MyCreatedMealDTO
import antoni.kalorie.core.networking.StorageDataProviderProtocol
import antoni.kalorie.core.networking.loadAsync
import antoni.kalorie.core.utils.Constants
import antoni.kalorie.core.utils.Log
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import kotlinx.coroutines.CancellationException
import java.time.Duration
import java.time.Instant

sealed class DeleteAccountError : Exception() {
    data class RequiresRecentLogin(val dataAlreadyDeleted: Boolean) : DeleteAccountError()
    data object MissingAppleAccessToken : DeleteAccountError()
}

interface DeleteAccountUseCaseProtocol {
    suspend operator fun invoke(skipDataWipe: Boolean = false)
}

class DeleteAccountUseCase(
    private val dataProvider: FirestoreDataProviderProtocol,
    private val authProvider: AuthProviderProtocol,
    private val authCommandProvider: AuthCommandProviderProtocol,
    private val snapshotStore: PendingMergeSnapshotStoreProtocol,
    private val storageProvider: StorageDataProviderProtocol,
    private val appleSignInProvider: AppleSignInProviderProtocol,
) : DeleteAccountUseCaseProtocol {

    // MARK: - Functions

    override suspend fun invoke(skipDataWipe: Boolean) {
        val userId = authProvider.userId ?: throw AuthError.NotAuthenticated
        val hasApple = AuthProviderKind.APPLE in authProvider.linkedProviderKinds
        val appleAccessToken = if (hasApple) reauthenticateWithApple() else null
        val authenticationDate = if (hasApple) null else authProvider.currentAuthenticationDate()
        if (authenticationDate != null && Duration.between(authenticationDate, Instant.now()).seconds > Constants.Auth.RECENT_LOGIN_THRESHOLD_SECONDS) {
            throw DeleteAccountError.RequiresRecentLogin(dataAlreadyDeleted = false)
        }
        // A snapshot left by a failed merge would otherwise be resumed into the fresh anonymous
        // account that follows the deletion. Idempotent, so a retry with skipDataWipe is safe.
        snapshotStore.delete()
        if (!skipDataWipe) wipeFirestoreData(userId)
        if (appleAccessToken != null) {
            try {
                authCommandProvider.revokeAppleToken(appleAccessToken)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.error(error, Constants.LogCategory.ACCOUNT)
            }
        }
        try {
            authCommandProvider.deleteCurrentUser()
        } catch (error: CancellationException) {
            throw error
        } catch (error: FirebaseAuthRecentLoginRequiredException) {
            throw DeleteAccountError.RequiresRecentLogin(dataAlreadyDeleted = true)
        }
    }

    // MARK: - Private

    private suspend fun reauthenticateWithApple(): String? {
        val accessToken = try {
            appleSignInProvider.reauthenticate().accessToken
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            if ((error as? FirebaseAuthException)?.errorCode == Constants.Auth.ErrorCode.USER_MISMATCH) throw ReauthenticateError.WrongAccount
            throw error
        }
        if (accessToken == null) Log.error(DeleteAccountError.MissingAppleAccessToken, Constants.LogCategory.ACCOUNT)
        return accessToken
    }

    private suspend fun wipeBodyWeightData(userId: String) {
        val bodyWeights: List<BodyWeightDTO> = dataProvider.loadAsync(from = Constants.Firestore.bodyWeights(userId))
        dataProvider.batchDeleteAsync(ids = bodyWeights.map { it.id }, from = Constants.Firestore.bodyWeights(userId))
        dataProvider.deleteAsync(id = Constants.Firestore.APP_SETTINGS_DOCUMENT_ID, from = Constants.Firestore.settings(userId))
    }

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
        wipeBodyWeightData(userId)
        dataProvider.deleteAsync(id = Constants.Firestore.FOOD_FREQUENCY_DOCUMENT_ID, from = Constants.Firestore.stats(userId))
        val submissions: List<FoodItemSubmissionDTO> = dataProvider.loadAsync(
            from = Constants.Firestore.FOOD_ITEM_SUBMISSIONS,
            field = Constants.Firestore.Field.SUBMITTED_BY,
            isEqualTo = userId,
            orderBy = Constants.Firestore.Field.SUBMITTED_AT,
            descending = true,
        )
        submissions.forEach { dataProvider.deleteAsync(id = it.id, from = Constants.Firestore.FOOD_ITEM_SUBMISSIONS) }
        // Listing the folder, rather than following the deleted submissions' URLs, also removes
        // orphans left by earlier failed writes. catalogPhotos is shared catalogue content and stays.
        storageProvider.listAsync("${Constants.Storage.SUBMISSION_PHOTOS_FOLDER}/$userId").forEach { storageProvider.deleteAsync(it) }
        val reports: List<FoodItemReportDTO> = dataProvider.loadAsync(
            from = Constants.Firestore.FOOD_ITEM_REPORTS,
            field = Constants.Firestore.Field.REPORTED_BY,
            isEqualTo = userId,
            orderBy = Constants.Firestore.Field.REPORTED_AT,
            descending = true,
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
