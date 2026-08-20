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
