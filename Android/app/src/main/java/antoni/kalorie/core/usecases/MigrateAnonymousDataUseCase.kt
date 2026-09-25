package antoni.kalorie.core.usecases

import antoni.kalorie.core.auth.AuthCommandProviderProtocol
import antoni.kalorie.core.auth.AuthError
import antoni.kalorie.core.auth.AuthProviderProtocol
import antoni.kalorie.core.auth.PendingMergeSnapshot
import antoni.kalorie.core.auth.PendingMergeSnapshotStoreProtocol
import antoni.kalorie.core.networking.FavouriteFoodDTO
import antoni.kalorie.core.networking.FirestoreDataProviderProtocol
import antoni.kalorie.core.networking.FoodConsumedDTO
import antoni.kalorie.core.networking.FoodItemPersonalPortionsDTO
import antoni.kalorie.core.networking.MyCreatedMealDTO
import antoni.kalorie.core.networking.batchSetAsync
import antoni.kalorie.core.networking.loadAsync
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
            PendingMergeSnapshot(
                sourceAnonymousUserId = sourceUserId,
                foodConsumed = foodConsumed.await(),
                favouriteFoods = favouriteFoods.await(),
                myCreatedMeals = myCreatedMeals.await(),
                foodItemPortions = foodItemPortions.await(),
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
        snapshotStore.delete()
    }
}
