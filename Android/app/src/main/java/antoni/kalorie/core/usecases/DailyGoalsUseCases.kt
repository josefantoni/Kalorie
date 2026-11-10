package antoni.kalorie.core.usecases

import antoni.kalorie.core.auth.AuthError
import antoni.kalorie.core.auth.AuthProviderProtocol
import antoni.kalorie.core.models.BodyWeightDayKey
import antoni.kalorie.core.models.DailyGoalDomain
import antoni.kalorie.core.models.DailyGoalHistory
import antoni.kalorie.core.models.GoalMacroMode
import antoni.kalorie.core.networking.DailyGoalsDTO
import antoni.kalorie.core.networking.FirestoreDataProviderProtocol
import antoni.kalorie.core.networking.loadAsync
import antoni.kalorie.core.networking.mergeAsync
import antoni.kalorie.core.utils.Constants
import java.time.Instant
import kotlin.math.abs

interface FetchDailyGoalsUseCaseProtocol {
    suspend operator fun invoke(): DailyGoalHistory
}

class FetchDailyGoalsUseCase(
    private val dataProvider: FirestoreDataProviderProtocol,
    private val authProvider: AuthProviderProtocol,
) : FetchDailyGoalsUseCaseProtocol {

    // MARK: - Functions

    override suspend fun invoke(): DailyGoalHistory {
        val userId = authProvider.userId ?: throw AuthError.NotAuthenticated
        val dto: DailyGoalsDTO? = dataProvider.loadAsync(
            id = Constants.Firestore.GOALS_DOCUMENT_ID,
            from = Constants.Firestore.settings(userId),
        )
        return dto?.asDomain() ?: DailyGoalHistory.empty
    }
}

sealed class DailyGoalError : Exception() {
    data object InvalidKcal : DailyGoalError()
    data object InvalidMacros : DailyGoalError()
}

interface SaveDailyGoalUseCaseProtocol {
    suspend operator fun invoke(goal: DailyGoalDomain): DailyGoalHistory
}

class SaveDailyGoalUseCase(
    private val fetchDailyGoals: FetchDailyGoalsUseCaseProtocol,
    private val dataProvider: FirestoreDataProviderProtocol,
    private val authProvider: AuthProviderProtocol,
    private val now: () -> Instant = Instant::now,
) : SaveDailyGoalUseCaseProtocol {

    // MARK: - Functions

    override suspend fun invoke(goal: DailyGoalDomain): DailyGoalHistory {
        val userId = authProvider.userId ?: throw AuthError.NotAuthenticated
        var entry = goal.copy(validFrom = BodyWeightDayKey.make(now()))
        val kcalGoal = entry.kcalGoal
        if (kcalGoal != null) {
            if (kcalGoal !in KCAL_RANGE) throw DailyGoalError.InvalidKcal
            if (entry.macroGrams == null) throw DailyGoalError.InvalidMacros
            val isPercent = entry.macroMode == GoalMacroMode.PERCENT
            val macroValues = if (isPercent) {
                listOf(entry.carbsPercent, entry.proteinPercent, entry.fatPercent)
            } else {
                listOf(entry.carbsGrams, entry.proteinGrams, entry.fatGrams)
            }
            val macroRange = if (isPercent) MACRO_PERCENT_RANGE else MACRO_GRAMS_RANGE
            if (!macroValues.all { (it ?: 0.0) in macroRange }) throw DailyGoalError.InvalidMacros
            if (isPercent) {
                val sum = (entry.carbsPercent ?: 0.0) + (entry.proteinPercent ?: 0.0) + (entry.fatPercent ?: 0.0)
                if (abs(sum - 100) >= PERCENT_SUM_TOLERANCE) throw DailyGoalError.InvalidMacros
            }
        }
        val current = fetchDailyGoals()
        val sameDay = current.entries.firstOrNull { it.validFrom == entry.validFrom }
        if (entry.kcalGoal == null && !entry.hasProfile && sameDay != null && sameDay.hasProfile) {
            entry = entry.copy(
                sex = sameDay.sex,
                birthYear = sameDay.birthYear,
                heightCm = sameDay.heightCm,
                weightKg = sameDay.weightKg,
                activityLevel = sameDay.activityLevel,
            )
        }
        val history = current.replacing(entry)
        dataProvider.mergeAsync(
            DailyGoalsDTO(history),
            id = Constants.Firestore.GOALS_DOCUMENT_ID,
            inCollection = Constants.Firestore.settings(userId),
        )
        return history
    }

    companion object {
        val KCAL_RANGE = 500.0..10000.0
        val MACRO_GRAMS_RANGE = 0.0..1000.0
        val MACRO_PERCENT_RANGE = 0.0..100.0
        private const val PERCENT_SUM_TOLERANCE = 0.001
    }
}
