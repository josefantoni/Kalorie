package antoni.kalorie.scaffold

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ScaffoldProbeViewModelTest {

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun onAppear_setsStateToLoadedWithMacroKitResult() = runTest {
        val sut = makeSUT()

        sut.onAppear()

        val state = sut.state.value
        check(state is ScaffoldProbeViewModel.State.Loaded)
        assertEquals(100, state.scaledCalories)
    }

    private fun makeSUT(): ScaffoldProbeViewModel = ScaffoldProbeViewModel()
}
