package antoni.kalorie.core.networking

import antoni.kalorie.core.models.FoodItemDomain
import antoni.kalorie.core.models.FoodItemKind
import antoni.kalorie.core.models.FoodMeasure
import antoni.kalorie.core.utils.epochSecondsAsDouble
import antoni.kalorie.core.utils.instantFromEpochSeconds
import antoni.kalorie.macrokit.energyKJFromMacros
import antoni.kalorie.textkit.foldDiacritics
import antoni.kalorie.textkit.searchTerms
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class FoodItemDTO(
    val id: String,
    @SerialName("cz_name") val czName: String,
    @SerialName("eng_name") val engName: String,
    @SerialName("cz_name_lowercase") val czNameLowercase: String,
    @SerialName("eng_name_lowercase") val engNameLowercase: String,
    @SerialName("cz_name_folded") val czNameFolded: String? = null,
    @SerialName("eng_name_folded") val engNameFolded: String? = null,
    @SerialName("cz_name_search_terms") val czNameSearchTerms: List<String>? = null,
    @SerialName("eng_name_search_terms") val engNameSearchTerms: List<String>? = null,
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
) {

    // MARK: - Init

    constructor(item: FoodItemDomain) : this(
        id = item.id,
        czName = item.czName,
        engName = item.engName,
        czNameLowercase = item.czName.lowercase(),
        engNameLowercase = item.engName.lowercase(),
        czNameFolded = foldDiacritics(item.czName.lowercase()),
        engNameFolded = foldDiacritics(item.engName.lowercase()),
        czNameSearchTerms = searchTerms(item.czName),
        engNameSearchTerms = searchTerms(item.engName),
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
    )

    // MARK: - Functions

    fun asDomain(): FoodItemDomain = FoodItemDomain(
        id = id,
        kind = FoodItemKind.CATALOGUE,
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
    )
}
