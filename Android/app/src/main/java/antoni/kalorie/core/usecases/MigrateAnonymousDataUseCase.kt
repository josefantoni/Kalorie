package antoni.kalorie.core.usecases

import antoni.kalorie.core.auth.AuthCommandProviderProtocol
import antoni.kalorie.core.auth.AuthError
import antoni.kalorie.core.auth.AuthProviderProtocol
import antoni.kalorie.core.auth.PendingMergeSnapshot
import antoni.kalorie.core.auth.PendingMergeSnapshotStoreProtocol
import antoni.kalorie.core.networking.AppSettingsDTO
import antoni.kalorie.core.networking.BodyWeightDTO
import antoni.kalorie.core.networking.FavouriteFoodDTO
import antoni.kalorie.core.networking.FirestoreDataProviderProtocol
import antoni.kalorie.core.networking.FoodConsumedDTO
import antoni.kalorie.core.networking.FoodItemPersonalPortionsDTO
import antoni.kalorie.core.networking.MergedAnonymousAccountDTO
import antoni.kalorie.core.networking.MyCreatedMealDTO
import antoni.kalorie.core.networking.batchSetAsync
import antoni.kalorie.core.networking.loadAsync
import antoni.kalorie.core.networking.setAsync
import antoni.kalorie.core.utils.Constants
import com.google.firebase.auth.AuthCredential
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

interface MigrateAnonymousDataUseCaseProtocol {
    suspend fun migrate(sourceUserId: String, credential: AuthCredential)
    suspend fun resumeIfNeeded()
}

class MigrateAnonymousDataUseCase(
    private val dataProvider: FirestoreDataProviderProtocol,
    private val authProvider: AuthProviderProtocol,
    private val authCommandProvider: AuthCommandProviderProtocol,
    private val snapshotStore: PendingMergeSnapshotStoreProtocol,
) : MigrateAnonymousDataUseCaseProtocol {

    // MARK: - Functions

    override suspend fun migrate(sourceUserId: String, credential: AuthCredential) {
        val snapshot = coroutineScope {
            val foodConsumed = async { dataProvider.loadAsync<FoodConsumedDTO>(from = Constants.Firestore.foodConsumed(sourceUserId)) }
            val favouriteFoods = async { dataProvider.loadAsync<FavouriteFoodDTO>(from = Constants.Firestore.favouriteFoods(sourceUserId)) }
            val myCreatedMeals = async { dataProvider.loadAsync<MyCreatedMealDTO>(from = Constants.Firestore.myCreatedMeals(sourceUserId)) }
            val foodItemPortions = async {
                dataProvider.loadAsync<FoodItemPersonalPortionsDTO>(from = Constants.Firestore.foodItemPortions(sourceUserId))
            }
            val bodyWeights = async { dataProvider.loadAsync<BodyWeightDTO>(from = Constants.Firestore.bodyWeights(sourceUserId)) }
            val appSettings = async {
                dataProvider.loadAsync<AppSettingsDTO>(
                    id = Constants.Firestore.APP_SETTINGS_DOCUMENT_ID,
                    from = Constants.Firestore.settings(sourceUserId),
                )
            }
            PendingMergeSnapshot(
                sourceAnonymousUserId = sourceUserId,
                foodConsumed = foodConsumed.await(),
                favouriteFoods = favouriteFoods.await(),
                myCreatedMeals = myCreatedMeals.await(),
                foodItemPortions = foodItemPortions.await(),
                bodyWeights = bodyWeights.await(),
                appSettings = appSettings.await(),
            )
        }
        snapshotStore.save(snapshot)
        authCommandProvider.signIn(credential)
        writeAndCleanup(snapshot)
    }

    override suspend fun resumeIfNeeded() {
        val snapshot = snapshotStore.load() ?: return
        val currentUserId = authProvider.userId ?: return
        if (currentUserId == snapshot.sourceAnonymousUserId) {
            snapshotStore.delete()
        } else {
            writeAndCleanup(snapshot)
        }
    }

    // MARK: - Private

    private suspend fun writeAndCleanup(snapshot: PendingMergeSnapshot) {
        val userId = authProvider.userId ?: throw AuthError.NotAuthenticated
        dataProvider.batchSetAsync(snapshot.foodConsumed.map { it to it.id }, inCollection = Constants.Firestore.foodConsumed(userId))
        dataProvider.batchSetAsync(snapshot.favouriteFoods.map { it to it.id }, inCollection = Constants.Firestore.favouriteFoods(userId))
        dataProvider.batchSetAsync(snapshot.myCreatedMeals.map { it to it.id }, inCollection = Constants.Firestore.myCreatedMeals(userId))
        dataProvider.batchSetAsync(snapshot.foodItemPortions.map { it to it.id }, inCollection = Constants.Firestore.foodItemPortions(userId))
        writeBodyWeights(snapshot, userId)
        writeAppSettings(snapshot, userId)
        dataProvider.setAsync(
            MergedAnonymousAccountDTO(id = snapshot.sourceAnonymousUserId),
            id = snapshot.sourceAnonymousUserId,
            inCollection = Constants.Firestore.mergedAnonymousAccounts(userId),
        )
        snapshotStore.delete()
    }

    private suspend fun writeBodyWeights(snapshot: PendingMergeSnapshot, userId: String) {
        if (snapshot.bodyWeights.isEmpty()) return
        val collection = Constants.Firestore.bodyWeights(userId)
        val accountDays = dataProvider.loadAsync<BodyWeightDTO>(from = collection).map { it.id }.toSet()
        val missing = snapshot.bodyWeights.filter { it.id !in accountDays }.map { it to it.id }
        dataProvider.batchSetAsync(missing, inCollection = collection)
    }

    private suspend fun writeAppSettings(snapshot: PendingMergeSnapshot, userId: String) {
        val anonymousSettings = snapshot.appSettings ?: return
        val collection = Constants.Firestore.settings(userId)
        val accountSettings = dataProvider.loadAsync<AppSettingsDTO>(id = Constants.Firestore.APP_SETTINGS_DOCUMENT_ID, from = collection)
        if (accountSettings != null) return
        dataProvider.setAsync(anonymousSettings, id = Constants.Firestore.APP_SETTINGS_DOCUMENT_ID, inCollection = collection)
    }
}
