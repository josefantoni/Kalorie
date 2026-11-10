package antoni.kalorie.core.models

import antoni.kalorie.macrokit.ActivityLevel
import antoni.kalorie.macrokit.basalMetabolicRate
import antoni.kalorie.macrokit.goalPercentChanges
import antoni.kalorie.macrokit.goalVariants
import antoni.kalorie.macrokit.gramsFromKcal
import antoni.kalorie.macrokit.kcalFromGrams
import kotlin.math.roundToInt

enum class GoalSex(val rawValue: String) {
    MALE("male"),
    FEMALE("female"),
    ;

    companion object {
        fun fromRawValue(rawValue: String): GoalSex? = entries.firstOrNull { it.rawValue == rawValue }
    }
}

enum class GoalMacroMode(val rawValue: String) {
    PERCENT("percent"),
    GRAMS("grams"),
    ;

    companion object {
        fun fromRawValue(rawValue: String): GoalMacroMode? = entries.firstOrNull { it.rawValue == rawValue }
    }
}

enum class GoalActivityLevel(val rawValue: String) {
    SEDENTARY("sedentary"),
    LIGHT("light"),
    MODERATE("moderate"),
    HIGH("high"),
    VERY_HIGH("very_high"),
    ;

    // MARK: - Properties

    val factor: Double
        get() = when (this) {
            SEDENTARY -> ActivityLevel.SEDENTARY.factor
            LIGHT -> ActivityLevel.LIGHT.factor
            MODERATE -> ActivityLevel.MODERATE.factor
            HIGH -> ActivityLevel.HIGH.factor
            VERY_HIGH -> ActivityLevel.VERY_HIGH.factor
        }

    companion object {
        fun fromRawValue(rawValue: String): GoalActivityLevel? = entries.firstOrNull { it.rawValue == rawValue }
    }
}

data class GoalMacroGrams(
    val protein: Double,
    val carbs: Double,
    val fat: Double,
) {

    // MARK: - Properties

    val kcal: Double get() = kcalFromGrams(protein = protein, carbohydrate = carbs, fat = fat)
}

data class DailyGoalDomain(
    val validFrom: String,
    val kcalGoal: Double? = null,
    val macroMode: GoalMacroMode = GoalMacroMode.PERCENT,
    val carbsPercent: Double? = null,
    val proteinPercent: Double? = null,
    val fatPercent: Double? = null,
    val carbsGrams: Double? = null,
    val proteinGrams: Double? = null,
    val fatGrams: Double? = null,
    val sex: GoalSex? = null,
    val birthYear: Int? = null,
    val heightCm: Double? = null,
    val weightKg: Double? = null,
    val activityLevel: GoalActivityLevel? = null,
) {

    // MARK: - Properties

    val isActive: Boolean get() = kcalGoal != null

    val hasProfile: Boolean
        get() = sex != null || birthYear != null || heightCm != null || weightKg != null || activityLevel != null

    val macroGrams: GoalMacroGrams?
        get() {
            val kcal = kcalGoal ?: return null
            return when (macroMode) {
                GoalMacroMode.PERCENT -> {
                    val carbs = carbsPercent ?: return null
                    val protein = proteinPercent ?: return null
                    val fat = fatPercent ?: return null
                    GoalMacroGrams(
                        protein = gramsFromKcal(kcal, protein, isFat = false),
                        carbs = gramsFromKcal(kcal, carbs, isFat = false),
                        fat = gramsFromKcal(kcal, fat, isFat = true),
                    )
                }
                GoalMacroMode.GRAMS -> {
                    val carbs = carbsGrams ?: return null
                    val protein = proteinGrams ?: return null
                    val fat = fatGrams ?: return null
                    GoalMacroGrams(protein = protein, carbs = carbs, fat = fat)
                }
            }
        }

    companion object {

        // MARK: - Functions

        fun ended(validFrom: String): DailyGoalDomain = DailyGoalDomain(validFrom = validFrom)
    }
}

data class DailyGoalHistory(val entries: List<DailyGoalDomain>) {

    // MARK: - Functions

    fun goal(onDayKey: String): DailyGoalDomain? = latestEntry(onDayKey)?.takeIf { it.isActive }

    fun latestEntry(onDayKey: String): DailyGoalDomain? = entries.filter { it.validFrom <= onDayKey }.maxByOrNull { it.validFrom }

    fun latestProfileEntry(onDayKey: String): DailyGoalDomain? = entries.filter { it.validFrom <= onDayKey && it.hasProfile }.maxByOrNull { it.validFrom }

    fun replacing(entry: DailyGoalDomain): DailyGoalHistory {
        val others = entries.filter { it.validFrom < entry.validFrom }
        return DailyGoalHistory(entries = (others + entry).sortedBy { it.validFrom })
    }

    companion object {
        val empty = DailyGoalHistory(entries = emptyList())
    }
}

data class GoalVariant(
    val percentChange: Int,
    val kcal: Int,
    val isRaisedToBasalRate: Boolean,
) {

    // MARK: - Properties

    val id: Int get() = percentChange
}

data class GoalEstimate(
    val basalRate: Int,
    val expenditure: Int,
    val variants: List<GoalVariant>,
) {
    companion object {

        // MARK: - Properties

        val percentChanges: List<Int> = goalPercentChanges

        // MARK: - Functions

        fun make(
            sex: GoalSex?,
            birthYear: Int?,
            heightCm: Double?,
            weightKg: Double?,
            activityLevel: GoalActivityLevel?,
            currentYear: Int,
        ): GoalEstimate? {
            if (sex == null || birthYear == null || heightCm == null || weightKg == null || activityLevel == null) return null
            val bmr = basalMetabolicRate(
                isMale = sex == GoalSex.MALE,
                weightKg = weightKg,
                heightCm = heightCm,
                ageYears = currentYear - birthYear,
            )
            val expenditure = bmr * activityLevel.factor
            return GoalEstimate(
                basalRate = bmr.roundToInt(),
                expenditure = expenditure.roundToInt(),
                variants = goalVariants(basalRate = bmr, expenditure = expenditure).map {
                    GoalVariant(percentChange = it.percentChange, kcal = it.kcal, isRaisedToBasalRate = it.isRaisedToBasalRate)
                },
            )
        }
    }
}

object GoalLimits {
    const val MIN_AGE = 18
    const val MAX_AGE = 99
    val heightRangeCm = 120..230
}
