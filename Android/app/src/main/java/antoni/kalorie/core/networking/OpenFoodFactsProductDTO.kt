package antoni.kalorie.core.networking

import antoni.kalorie.core.models.FoodItemDomain
import antoni.kalorie.core.models.FoodItemKind
import antoni.kalorie.macrokit.energyKJFromMacros
import antoni.kalorie.textkit.decodeHtmlEntities
import java.time.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

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
        )
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
