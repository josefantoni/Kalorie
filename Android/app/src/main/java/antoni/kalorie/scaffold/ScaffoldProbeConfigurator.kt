package antoni.kalorie.scaffold

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import kotlinx.serialization.Serializable

@Serializable
private data object ScaffoldProbeKey : NavKey

class ScaffoldProbeConfigurator {

    // MARK: - Functions

    @Composable
    fun createView() {
        val backStack = rememberNavBackStack(ScaffoldProbeKey)
        NavDisplay(
            backStack = backStack,
            entryDecorators = listOf(rememberViewModelStoreNavEntryDecorator()),
            entryProvider = entryProvider {
                entry<ScaffoldProbeKey> {
                    val viewModel = viewModel<ScaffoldProbeViewModel>()
                    ScaffoldProbeScreen(viewModel = viewModel)
                }
            },
        )
    }
}
