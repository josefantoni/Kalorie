package antoni.kalorie.features.dashboard

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.viewmodel.compose.viewModel
import antoni.kalorie.core.auth.AuthProviderProtocol
import antoni.kalorie.core.models.BodyWeightUnit
import antoni.kalorie.core.networking.FirestoreDataProviderProtocol
import antoni.kalorie.core.usecases.DeleteBodyWeightUseCase
import antoni.kalorie.core.usecases.FetchBodyWeightsUseCase
import java.time.Instant

class BodyWeightHistoryConfigurator(
    private val dataProvider: FirestoreDataProviderProtocol,
    private val authProvider: AuthProviderProtocol,
) {

    // MARK: - Functions

    @Composable
    fun createView(unit: BodyWeightUnit, selectedDay: Instant, onBack: () -> Unit, onChanged: () -> Unit) {
        val currentOnChanged by rememberUpdatedState(onChanged)
        val viewModel = viewModel {
            BodyWeightHistoryViewModel(
                unit = unit,
                selectedDay = selectedDay,
                fetchBodyWeights = FetchBodyWeightsUseCase(dataProvider, authProvider),
                deleteBodyWeight = DeleteBodyWeightUseCase(dataProvider, authProvider),
                onChanged = { currentOnChanged() },
            )
        }
        BodyWeightHistoryView(viewModel = viewModel, onBack = onBack)
    }
}
