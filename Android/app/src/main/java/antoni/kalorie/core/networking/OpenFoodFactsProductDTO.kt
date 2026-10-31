package antoni.kalorie.core.networking

import antoni.kalorie.core.models.FoodItemDomain
import antoni.kalorie.core.models.FoodItemKind
import antoni.kalorie.core.models.FoodMeasure
import antoni.kalorie.core.models.FoodPortionDomain
import antoni.kalorie.core.models.FoodPortionValidation
import antoni.kalorie.core.utils.Constants
import antoni.kalorie.macrokit.energyKJFromMacros
import antoni.kalorie.textkit.decodeHtmlEntities
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import java.time.Instant

private val PLAIN_DECIMAL = Regex("^[0-9]+(\\.[0-9]+)?$")

@Serializable
data class OpenFoodFactsResponseDTO(
    val products: List<OpenFoodFactsProductDTO>,
)

@Serializable
data class OpenFoodFactsBarcodeResponseDTO(
    val status: Int,
    val product: OpenFoodFactsProductDTO? = null,
)

@Serializable
data class OpenFoodFactsProductDTO(
    val code: String,
    @SerialName("product_name_cs") val productNameCs: String? = null,
    @SerialName("product_name_en") val productNameEn: String? = null,
    @SerialName("product_name") val productName: String? = null,
    val nutriments: OpenFoodFactsNutrimentsDTO? = null,
    @SerialName("image_front_url") val imageFrontUrl: String? = null,
    @SerialName("serving_quantity") val servingQuantity: JsonElement? = null,
    @SerialName("serving_quantity_unit") val servingQuantityUnit: JsonElement? = null,
) {

    // MARK: - Functions

    fun asDomain(): FoodItemDomain? {
        // Reject products missing calories or name — a partial item would show 0 kcal in the UI,
        // which is worse than "not found". Callers treat null as product not found.
        val nutriments = nutriments ?: return null
        val kcal = nutriments.energyKcal100g?.takeIf { it > 0 } ?: return null
        val rawName = firstNonEmpty(productNameCs, productNameEn, productName) ?: return null
        val fat = nutriments.fat100g ?: 0.0
        val saturatedFat = nutriments.saturatedFat100g ?: 0.0
        val carbohydrate = nutriments.carbohydrates100g ?: 0.0
        val protein = nutriments.proteins100g ?: 0.0
        val rawOriginalName = firstNonEmpty(productNameEn, productName) ?: rawName
        val unit = (servingQuantityUnit as? JsonPrimitive)?.takeIf { it.isString }?.content?.trim { it in Constants.OpenFoodFacts.SERVING_QUANTITY_WHITESPACE }?.lowercase()
        val validServing = validServingQuantity(unit)
        return FoodItemDomain(
            id = code,
            kind = FoodItemKind.EXTERNAL,
            czName = decodeHtmlEntities(rawName),
            engName = decodeHtmlEntities(rawOriginalName),
            weight = 100.0,
            date = Instant.now(),
            energyKJ = nutriments.energyKJ100g ?: energyKJFromMacros(fat = fat, carbohydrate = carbohydrate, protein = protein),
            caloriesPerHundredGrams = kcal,
            fat = fat,
            fatSaturated = saturatedFat,
            fatUnsaturatedFattyAcids = maxOf(0.0, fat - saturatedFat),
            carbohydrate = carbohydrate,
            carbohydratePureSugar = nutriments.sugars100g ?: 0.0,
            fiber = nutriments.fiber100g ?: 0.0,
            protein = protein,
            salt = nutriments.salt100g ?: 0.0,
            portions = servingPortions(validServing),
            measure = if (validServing != null && unit == Constants.OpenFoodFacts.MILLILITRES_UNIT) FoodMeasure.MILLILITRES else FoodMeasure.GRAMS,
            photoUrl = imageFrontUrl,
        )
    }

    private fun servingPortions(validServing: Double?): List<FoodPortionDomain> {
        if (validServing == null || validServing == Constants.OpenFoodFacts.PER_HUNDRED_AMOUNT) return emptyList()
        return listOf(FoodPortionDomain(name = Constants.OpenFoodFacts.SERVING_PORTION_NAME, grams = validServing))
    }

    private fun validServingQuantity(unit: String?): Double? {
        if (unit != Constants.OpenFoodFacts.GRAMS_UNIT && unit != Constants.OpenFoodFacts.MILLILITRES_UNIT) return null
        val quantity = parsedServingQuantity() ?: return null
        if (!(quantity * 10).isFinite()) return null
        val rounded = Math.round(quantity * 10) / 10.0
        if (FoodPortionValidation.validate(Constants.OpenFoodFacts.SERVING_PORTION_NAME, rounded) != null) return null
        return rounded.takeIf { it <= Constants.OpenFoodFacts.MAX_SERVING_QUANTITY }
    }

    private fun parsedServingQuantity(): Double? {
        val primitive = servingQuantity as? JsonPrimitive ?: return null
        if (!primitive.isString) return primitive.doubleOrNull
        return primitive.content.trim { it in Constants.OpenFoodFacts.SERVING_QUANTITY_WHITESPACE }.takeIf(PLAIN_DECIMAL::matches)?.toDouble()
    }

    private fun firstNonEmpty(vararg names: String?): String? = names.firstOrNull { it != null && it.isNotBlank() }
}

@Serializable
data class OpenFoodFactsNutrimentsDTO(
    @SerialName("energy-kcal_100g") val energyKcal100g: Double? = null,
    @SerialName("energy_100g") val energyKJ100g: Double? = null,
    @SerialName("fat_100g") val fat100g: Double? = null,
    @SerialName("saturated-fat_100g") val saturatedFat100g: Double? = null,
    @SerialName("carbohydrates_100g") val carbohydrates100g: Double? = null,
    @SerialName("sugars_100g") val sugars100g: Double? = null,
    @SerialName("fiber_100g") val fiber100g: Double? = null,
    @SerialName("proteins_100g") val proteins100g: Double? = null,
    @SerialName("salt_100g") val salt100g: Double? = null,
)
