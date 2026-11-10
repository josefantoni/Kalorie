package antoni.kalorie.features.dailygoals

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.viewmodel.compose.viewModel
import antoni.kalorie.core.auth.AuthProviderProtocol
import antoni.kalorie.core.models.BodyWeightUnit
import antoni.kalorie.core.models.DailyGoalHistory
import antoni.kalorie.core.networking.FirestoreDataProviderProtocol
import antoni.kalorie.core.usecases.FetchBodyWeightsUseCase
import antoni.kalorie.core.usecases.FetchDailyGoalsUseCase
import antoni.kalorie.core.usecases.SaveBodyWeightUseCase
import antoni.kalorie.core.usecases.SaveDailyGoalUseCase

class DailyGoalsConfigurator(
    private val dataProvider: FirestoreDataProviderProtocol,
    private val authProvider: AuthProviderProtocol,
) {

    // MARK: - Functions

    @Composable
    fun createView(history: DailyGoalHistory, unit: BodyWeightUnit, onBack: () -> Unit, onChanged: (DailyGoalHistory) -> Unit) {
        val currentOnChanged by rememberUpdatedState(onChanged)
        val viewModel = viewModel {
            DailyGoalsViewModel(
                history = history,
                unit = unit,
                fetchBodyWeights = FetchBodyWeightsUseCase(dataProvider, authProvider),
                saveDailyGoal = SaveDailyGoalUseCase(
                    fetchDailyGoals = FetchDailyGoalsUseCase(dataProvider, authProvider),
                    dataProvider = dataProvider,
                    authProvider = authProvider,
                ),
                saveBodyWeight = SaveBodyWeightUseCase(dataProvider, authProvider),
                onChanged = { currentOnChanged(it) },
            )
        }
        DailyGoalsView(viewModel = viewModel, onBack = onBack)
    }
}
