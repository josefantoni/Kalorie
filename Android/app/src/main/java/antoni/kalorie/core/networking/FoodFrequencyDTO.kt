package antoni.kalorie.core.networking

import antoni.kalorie.core.models.FoodItemDomain
import antoni.kalorie.core.models.FoodItemKind
import antoni.kalorie.core.models.FoodMeasure
import antoni.kalorie.core.utils.epochSecondsAsDouble
import antoni.kalorie.core.utils.instantFromEpochSeconds
import antoni.kalorie.macrokit.energyKJFromMacros
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

@Serializable
data class FoodFrequencyItemDTO(
    val id: String,
    @SerialName("food_item_kind") val foodItemKind: FoodItemKind,
    @SerialName("cz_name") val czName: String,
    @SerialName("eng_name") val engName: String,
    val weight: Double,
    val date: Double,
    @SerialName("energy_kj") val energyKJ: Double? = null,
    @SerialName("calories_per_hundred_grams") val caloriesPerHundredGrams: Double,
    val fat: Double,
    @SerialName("fat_saturated") val fatSaturated: Double? = null,
    @SerialName("fat_unsaturated_fatty_acids") val fatUnsaturatedFattyAcids: Double,
    val carbohydrate: Double,
    @SerialName("carbohydrate_pure_sugar") val carbohydratePureSugar: Double,
    val fiber: Double? = null,
    val protein: Double,
    val salt: Double,
    val portions: List<FoodPortionDTO>? = null,
    @SerialName("measure_unit") val measureUnit: String? = null,
    @SerialName("alcohol_by_volume") val alcoholByVolume: Double? = null,
) {

    // MARK: - Init

    constructor(item: FoodItemDomain) : this(
        id = item.id,
        foodItemKind = item.kind,
        czName = item.czName,
        engName = item.engName,
        weight = item.weight,
        date = item.date.epochSecondsAsDouble(),
        energyKJ = item.energyKJ,
        caloriesPerHundredGrams = item.caloriesPerHundredGrams,
        fat = item.fat,
        fatSaturated = item.fatSaturated,
        fatUnsaturatedFattyAcids = item.fatUnsaturatedFattyAcids,
        carbohydrate = item.carbohydrate,
        carbohydratePureSugar = item.carbohydratePureSugar,
        fiber = item.fiber,
        protein = item.protein,
        salt = item.salt,
        portions = item.portions.map(::FoodPortionDTO),
        measureUnit = item.measure.rawValue,
        alcoholByVolume = item.alcoholByVolume,
    )

    // MARK: - Functions

    fun asDomain(): FoodItemDomain = FoodItemDomain(
        id = id,
        kind = foodItemKind,
        czName = czName,
        engName = engName,
        weight = weight,
        date = instantFromEpochSeconds(date),
        energyKJ = energyKJ ?: energyKJFromMacros(fat = fat, carbohydrate = carbohydrate, protein = protein),
        caloriesPerHundredGrams = caloriesPerHundredGrams,
        fat = fat,
        fatSaturated = fatSaturated,
        fatUnsaturatedFattyAcids = fatUnsaturatedFattyAcids,
        carbohydrate = carbohydrate,
        carbohydratePureSugar = carbohydratePureSugar,
        fiber = fiber,
        protein = protein,
        salt = salt,
        portions = portions?.map(FoodPortionDTO::asDomain) ?: emptyList(),
        measure = measureUnit?.let(FoodMeasure::fromRawValue) ?: FoodMeasure.GRAMS,
        alcoholByVolume = alcoholByVolume,
    )
}

@Serializable
data class FoodFrequencyEntryDTO(
    val count: Int,
    @SerialName("last_logged_at") val lastLoggedAt: Double,
    val item: FoodFrequencyItemDTO,
)

@Serializable
data class FoodFrequencyDocumentDTO(
    val entries: Map<String, JsonElement> = emptyMap(),
) {

    // MARK: - Functions

    fun decodedEntries(): Map<String, FoodFrequencyEntryDTO> = entries.mapNotNull { (id, element) ->
        runCatching { json.decodeFromJsonElement(FoodFrequencyEntryDTO.serializer(), element) }.getOrNull()?.let { id to it }
    }.toMap()

    fun undecodableIds(): List<String> = (entries.keys - decodedEntries().keys).sorted()
}

private val json = Json { ignoreUnknownKeys = true }
