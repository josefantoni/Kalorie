package antoni.kalorie.core.usecases

import antoni.kalorie.core.models.DailyGoalDomain
import antoni.kalorie.core.models.DailyGoalHistory

data class FetchDailyGoalsUseCaseFake(
    val stubbedHistory: DailyGoalHistory = DailyGoalHistory.empty,
    val shouldThrow: Boolean = false,
) : FetchDailyGoalsUseCaseProtocol {

    // MARK: - Functions

    override suspend fun invoke(): DailyGoalHistory {
        if (shouldThrow) throw RuntimeException("unknown")
        return stubbedHistory
    }
}

data class SaveDailyGoalUseCaseFake(
    val shouldThrow: Boolean = false,
) : SaveDailyGoalUseCaseProtocol {

    // MARK: - Functions

    override suspend fun invoke(goal: DailyGoalDomain): DailyGoalHistory {
        if (shouldThrow) throw RuntimeException("unknown")
        return DailyGoalHistory(entries = listOf(goal))
    }
}
