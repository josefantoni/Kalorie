package antoni.kalorie.macrokit

private const val KCAL_PER_GRAM_PROTEIN = 4.0
private const val KCAL_PER_GRAM_CARBOHYDRATE = 4.0
private const val KCAL_PER_GRAM_FAT = 9.0

// Mifflin-St Jeor
fun basalMetabolicRate(isMale: Boolean, weightKg: Double, heightCm: Double, ageYears: Int): Double =
    10 * weightKg + 6.25 * heightCm - 5 * ageYears + (if (isMale) 5.0 else -161.0)

fun kcalFromGrams(protein: Double, carbohydrate: Double, fat: Double): Double =
    protein * KCAL_PER_GRAM_PROTEIN + carbohydrate * KCAL_PER_GRAM_CARBOHYDRATE + fat * KCAL_PER_GRAM_FAT

fun gramsFromKcal(kcal: Double, percent: Double, isFat: Boolean): Double =
    kcal * percent / 100 / (if (isFat) KCAL_PER_GRAM_FAT else KCAL_PER_GRAM_PROTEIN)

fun energyShares(protein: Double, carbohydrate: Double, fat: Double): List<Double> {
    val total = kcalFromGrams(protein, carbohydrate, fat)
    if (total <= 0) return listOf(0.0, 0.0, 0.0)
    return listOf(
        protein * KCAL_PER_GRAM_PROTEIN / total,
        carbohydrate * KCAL_PER_GRAM_CARBOHYDRATE / total,
        fat * KCAL_PER_GRAM_FAT / total,
    )
}
