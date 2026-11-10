package antoni.kalorie.macrokit

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

enum class ActivityLevel(val factor: Double) {
    SEDENTARY(1.2),
    LIGHT(1.375),
    MODERATE(1.55),
    HIGH(1.725),
    VERY_HIGH(1.9),
}

private const val GRAMS_KCAL_TOLERANCE = 10.0

val goalPercentChanges: List<Int> = listOf(-10, -5, 0, 5, 10)

data class GoalVariantKcal(val percentChange: Int, val kcal: Int, val isRaisedToBasalRate: Boolean)

fun goalVariants(basalRate: Double, expenditure: Double): List<GoalVariantKcal> =
    goalPercentChanges.map { change ->
        val raw = expenditure * (1 + change / 100.0)
        val isRaised = raw < basalRate
        GoalVariantKcal(change, (if (isRaised) basalRate else raw).roundToInt(), isRaised)
    }

fun referenceKcal(isMale: Boolean): Int {
    val bmr = if (isMale) basalMetabolicRate(true, 80.0, 178.0, 30) else basalMetabolicRate(false, 65.0, 165.0, 30)
    return (bmr * ActivityLevel.LIGHT.factor / 100).roundToInt() * 100
}

fun roundedToHundred(values: List<Double>): List<Double> {
    val floors = values.map { floor(it) }.toMutableList()
    var missing = (100 - floors.sum()).toInt()
    val byRemainder = values.indices.sortedByDescending { values[it] - floors[it] }
    for (index in byRemainder) {
        if (missing <= 0) break
        floors[index] += 1.0
        missing -= 1
    }
    return floors
}

fun gramsKcalDiffersFromGoal(gramsKcal: Double, goalKcal: Double): Boolean =
    abs(gramsKcal - goalKcal) > GRAMS_KCAL_TOLERANCE
