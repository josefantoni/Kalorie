package antoni.kalorie.core.auth

import antoni.kalorie.core.networking.FavouriteFoodDTO
import antoni.kalorie.core.networking.FoodConsumedDTO
import antoni.kalorie.core.networking.FoodItemPersonalPortionsDTO
import antoni.kalorie.core.networking.MyCreatedMealDTO
import java.io.File
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class PendingMergeSnapshot(
    val sourceAnonymousUserId: String,
    val foodConsumed: List<FoodConsumedDTO>,
    val favouriteFoods: List<FavouriteFoodDTO> = emptyList(),
    val myCreatedMeals: List<MyCreatedMealDTO> = emptyList(),
    val foodItemPortions: List<FoodItemPersonalPortionsDTO> = emptyList(),
)

interface PendingMergeSnapshotStoreProtocol {
    fun save(snapshot: PendingMergeSnapshot)
    fun load(): PendingMergeSnapshot?
    fun delete()
}

class PendingMergeSnapshotStore(directory: File) : PendingMergeSnapshotStoreProtocol {

    // MARK: - Properties

    private val file = File(directory, "pending-merge.json")
    private val json = Json { ignoreUnknownKeys = true }

    // MARK: - Functions

    override fun save(snapshot: PendingMergeSnapshot) {
        val temporaryFile = File(file.parentFile, "pending-merge.json.tmp")
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
