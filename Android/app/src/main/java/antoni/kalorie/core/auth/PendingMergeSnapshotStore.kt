package antoni.kalorie.core.auth

import antoni.kalorie.core.networking.AppSettingsDTO
import antoni.kalorie.core.networking.BodyWeightDTO
import antoni.kalorie.core.networking.DailyGoalsDTO
import antoni.kalorie.core.networking.FavouriteFoodDTO
import antoni.kalorie.core.networking.FoodConsumedDTO
import antoni.kalorie.core.networking.FoodItemPersonalPortionsDTO
import antoni.kalorie.core.networking.MyCreatedMealDTO
import antoni.kalorie.core.utils.Constants
import antoni.kalorie.textkit.slovakName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
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
    val dailyGoals: DailyGoalsDTO? = null,
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
        val element = json.parseToJsonElement(file.readText())
        val normalized = if (element is JsonObject) fillingMissingSlovakNames(element) else element
        return json.decodeFromJsonElement(PendingMergeSnapshot.serializer(), normalized)
    }

    override fun delete() {
        if (!file.exists()) return
        check(file.delete()) { "Could not delete the pending merge snapshot" }
    }

    private fun fillingMissingSlovakNames(root: JsonObject): JsonObject = root
        .mapObjects("foodConsumed") { it.withSlovakName() }
        .mapObjects("favouriteFoods") { it.withSlovakName() }
        .mapObjects("myCreatedMeals") { meal -> meal.mapObjects("ingredients") { it.withSlovakName() } }

    private fun JsonObject.mapObjects(key: String, transform: (JsonObject) -> JsonObject): JsonObject {
        val array = this[key] as? JsonArray ?: return this
        val mapped = JsonArray(array.map { element -> (element as? JsonObject)?.let(transform) ?: element })
        return JsonObject(this + (key to mapped))
    }

    private fun JsonObject.withSlovakName(): JsonObject {
        if ("sk_name" in this) return this
        fun text(key: String) = (this[key] as? JsonPrimitive)?.contentOrNull
        return JsonObject(this + ("sk_name" to JsonPrimitive(slovakName(null, text("cz_name"), text("eng_name")))))
    }
}
