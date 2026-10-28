package antoni.kalorie.features.dashboard

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import antoni.kalorie.core.auth.AuthProviderProtocol
import antoni.kalorie.core.models.BodyWeightUnit
import antoni.kalorie.core.networking.FirestoreDataProviderProtocol
import antoni.kalorie.core.usecases.DeleteBodyWeightUseCase
import antoni.kalorie.core.usecases.FetchBodyWeightsUseCase

class BodyWeightHistoryConfigurator(
    private val dataProvider: FirestoreDataProviderProtocol,
    private val authProvider: AuthProviderProtocol,
) {

    // MARK: - Functions

    @Composable
    fun createView(unit: BodyWeightUnit, onBack: () -> Unit, onChanged: () -> Unit) {
        val viewModel = viewModel {
            BodyWeightHistoryViewModel(
                unit = unit,
                fetchBodyWeights = FetchBodyWeightsUseCase(dataProvider, authProvider),
                deleteBodyWeight = DeleteBodyWeightUseCase(dataProvider, authProvider),
                onChanged = onChanged,
            )
        }
        BodyWeightHistoryView(viewModel = viewModel, onBack = onBack)
    }
}
