import kotlin.math.roundToInt

data class Macros(
    val calories: Int,
    val protein: Double,
    val carbohydrate: Double,
    val carbohydrateSugar: Double,
    val fat: Double,
    val fatUnsaturated: Double,
    val fiber: Double,
    val salt: Double,
)

fun scaledCalories(caloriesPerHundredGrams: Double, ratio: Double): Int =
    (caloriesPerHundredGrams * ratio).roundToInt()

// Derives a per-100g basis for an entry written before caloriesPerHundredGrams was stored, from
// its already-rounded absolute calories. Used once, at decode time, never to compute a new value.
fun caloriesPerHundredGrams(calories: Int, weight: Double): Double =
    if (weight > 0) calories / weight * 100 else 0.0

fun Macros.scaled(factor: Double): Macros = Macros(
    calories = (calories * factor).roundToInt(),
    protein = protein * factor,
    carbohydrate = carbohydrate * factor,
    carbohydrateSugar = carbohydrateSugar * factor,
    fat = fat * factor,
    fatUnsaturated = fatUnsaturated * factor,
    fiber = fiber * factor,
    salt = salt * factor,
)

private const val KILOJOULES_PER_GRAM_FAT = 37.0
private const val KILOJOULES_PER_GRAM_CARBOHYDRATE = 17.0
private const val KILOJOULES_PER_GRAM_PROTEIN = 17.0

// EU Regulation 1169/2011, Annex XIV general conversion factors — used when a source's own
// energy value is missing, so a food is never displayed at 0 kJ despite having real macros.
fun energyKJFromMacros(fat: Double, carbohydrate: Double, protein: Double): Double =
    fat * KILOJOULES_PER_GRAM_FAT + carbohydrate * KILOJOULES_PER_GRAM_CARBOHYDRATE + protein * KILOJOULES_PER_GRAM_PROTEIN

fun weightedMeanPerHundredGrams(values: List<Double>, grams: List<Double>): Double {
    val totalGrams = grams.sum()
    if (totalGrams == 0.0) return 0.0
    val weightedSum = values.indices.sumOf { values[it] * grams[it] }
    return weightedSum / totalGrams
}

fun List<Macros>.total(): Macros = fold(
    Macros(
        calories = 0,
        protein = 0.0,
        carbohydrate = 0.0,
        carbohydrateSugar = 0.0,
        fat = 0.0,
        fatUnsaturated = 0.0,
        fiber = 0.0,
        salt = 0.0,
    )
) { acc, macros ->
    Macros(
        calories = acc.calories + macros.calories,
        protein = acc.protein + macros.protein,
        carbohydrate = acc.carbohydrate + macros.carbohydrate,
        carbohydrateSugar = acc.carbohydrateSugar + macros.carbohydrateSugar,
        fat = acc.fat + macros.fat,
        fatUnsaturated = acc.fatUnsaturated + macros.fatUnsaturated,
        fiber = acc.fiber + macros.fiber,
        salt = acc.salt + macros.salt,
    )
}
