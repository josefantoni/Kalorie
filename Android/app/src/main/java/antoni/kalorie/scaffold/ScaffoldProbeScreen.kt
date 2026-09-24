package antoni.kalorie.scaffold

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ScaffoldProbeScreen(viewModel: ScaffoldProbeViewModel) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) { viewModel.onAppear() }

    Scaffold { innerPadding ->
        val label = when (val current = state) {
            is ScaffoldProbeViewModel.State.Idle -> "Loading"
            is ScaffoldProbeViewModel.State.Loaded -> "MacroKit says ${current.scaledCalories} kcal"
        }
        Text(
            text = label,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}
