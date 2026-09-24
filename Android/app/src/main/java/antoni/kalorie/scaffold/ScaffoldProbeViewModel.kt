package antoni.kalorie.scaffold

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import antoni.kalorie.macrokit.scaledCalories
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ScaffoldProbeViewModel : ViewModel() {

    // MARK: - Properties

    sealed interface State {
        data object Idle : State
        data class Loaded(val scaledCalories: Int) : State
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state

    // MARK: - Functions

    fun onAppear() {
        viewModelScope.launch {
            _state.value = State.Loaded(scaledCalories(caloriesPerHundredGrams = 250.0, ratio = 0.4))
        }
    }
}
