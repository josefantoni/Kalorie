package antoni.kalorie.core.auth

import antoni.kalorie.core.networking.AppSettingsDTO
import antoni.kalorie.core.networking.BodyWeightDTO
import antoni.kalorie.core.networking.FavouriteFoodDTO
import antoni.kalorie.core.networking.FoodConsumedDTO
import antoni.kalorie.core.networking.FoodItemPersonalPortionsDTO
import antoni.kalorie.core.networking.MyCreatedMealDTO
import antoni.kalorie.core.utils.Constants
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class PendingMergeSnapshot(
    val sourceAnonymousUserId: String,
    val foodConsumed: List<FoodConsumedDTO>,
    val favouriteFoods: List<FavouriteFoodDTO> = emptyList(),
    val myCreatedMeals: List<MyCreatedMealDTO> = emptyList(),
    val foodItemPortions: List<FoodItemPersonalPortionsDTO> = emptyList(),
    val bodyWeights: List<BodyWeightDTO> = emptyList(),
    val appSettings: AppSettingsDTO? = null,
)

interface PendingMergeSnapshotStoreProtocol {
    fun save(snapshot: PendingMergeSnapshot)
    fun load(): PendingMergeSnapshot?
    fun delete()
}

class PendingMergeSnapshotStore(directory: File) : PendingMergeSnapshotStoreProtocol {

    // MARK: - Properties

    private val file = File(directory, Constants.Auth.PENDING_MERGE_SNAPSHOT_FILE_NAME)
    private val json = Json { ignoreUnknownKeys = true }

    // MARK: - Functions

    override fun save(snapshot: PendingMergeSnapshot) {
        val temporaryFile = File(file.parentFile, "${Constants.Auth.PENDING_MERGE_SNAPSHOT_FILE_NAME}.tmp")
        temporaryFile.writeText(json.encodeToString(PendingMergeSnapshot.serializer(), snapshot))
        if (!temporaryFile.renameTo(file)) {
            file.delete()
            check(temporaryFile.renameTo(file)) { "Could not replace the pending merge snapshot" }
        }
    }

    override fun load(): PendingMergeSnapshot? {
        if (!file.exists()) return null
        return json.decodeFromString(PendingMergeSnapshot.serializer(), file.readText())
    }

    override fun delete() {
        if (!file.exists()) return
        check(file.delete()) { "Could not delete the pending merge snapshot" }
    }
}
