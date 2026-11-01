package antoni.kalorie.core.usecases

import antoni.kalorie.core.auth.AppleSignInProviderProtocol
import antoni.kalorie.core.auth.AuthCommandProviderProtocol
import antoni.kalorie.core.auth.AuthError
import antoni.kalorie.core.auth.AuthProviderKind
import antoni.kalorie.core.auth.AuthProviderProtocol
import antoni.kalorie.core.auth.PendingMergeSnapshotStoreProtocol
import antoni.kalorie.core.networking.FirestoreDataProviderProtocol
import antoni.kalorie.core.networking.StorageDataProviderProtocol
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
        val bodyWeightIds = dataProvider.loadDocumentIdsAsync(from = Constants.Firestore.bodyWeights(userId))
        dataProvider.batchDeleteAsync(ids = bodyWeightIds, from = Constants.Firestore.bodyWeights(userId))
        dataProvider.deleteAsync(id = Constants.Firestore.APP_SETTINGS_DOCUMENT_ID, from = Constants.Firestore.settings(userId))
    }

    private suspend fun wipeFirestoreData(userId: String) {
        val mealTypeIds = dataProvider.loadDocumentIdsAsync(from = Constants.Firestore.mealTypes(userId))
        dataProvider.batchDeleteAsync(ids = mealTypeIds, from = Constants.Firestore.mealTypes(userId))
        val foodIds = dataProvider.loadDocumentIdsAsync(from = Constants.Firestore.foodConsumed(userId))
        dataProvider.batchDeleteAsync(ids = foodIds, from = Constants.Firestore.foodConsumed(userId))
        val favouriteFoodIds = dataProvider.loadDocumentIdsAsync(from = Constants.Firestore.favouriteFoods(userId))
        dataProvider.batchDeleteAsync(ids = favouriteFoodIds, from = Constants.Firestore.favouriteFoods(userId))
        val myCreatedMealIds = dataProvider.loadDocumentIdsAsync(from = Constants.Firestore.myCreatedMeals(userId))
        dataProvider.batchDeleteAsync(ids = myCreatedMealIds, from = Constants.Firestore.myCreatedMeals(userId))
        val foodItemPortionIds = dataProvider.loadDocumentIdsAsync(from = Constants.Firestore.foodItemPortions(userId))
        dataProvider.batchDeleteAsync(ids = foodItemPortionIds, from = Constants.Firestore.foodItemPortions(userId))
        wipeBodyWeightData(userId)
        dataProvider.deleteAsync(id = Constants.Firestore.FOOD_FREQUENCY_DOCUMENT_ID, from = Constants.Firestore.stats(userId))
        val submissionIds = dataProvider.loadDocumentIdsAsync(
            from = Constants.Firestore.FOOD_ITEM_SUBMISSIONS,
            field = Constants.Firestore.Field.SUBMITTED_BY,
            isEqualTo = userId,
        )
        dataProvider.batchDeleteAsync(ids = submissionIds, from = Constants.Firestore.FOOD_ITEM_SUBMISSIONS)
        // Listing the folder, rather than following the deleted submissions' URLs, also removes
        // orphans left by earlier failed writes. catalogPhotos is shared catalogue content and stays.
        storageProvider.listAsync("${Constants.Storage.SUBMISSION_PHOTOS_FOLDER}/$userId").forEach { storageProvider.deleteAsync(it) }
        val reportIds = dataProvider.loadDocumentIdsAsync(
            from = Constants.Firestore.FOOD_ITEM_REPORTS,
            field = Constants.Firestore.Field.REPORTED_BY,
            isEqualTo = userId,
        )
        dataProvider.batchDeleteAsync(ids = reportIds, from = Constants.Firestore.FOOD_ITEM_REPORTS)
        try {
            dataProvider.deleteAsync(id = userId, from = Constants.Firestore.USERS)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.error(error, Constants.LogCategory.ACCOUNT)
        }
    }
}
