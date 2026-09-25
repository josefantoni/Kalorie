package antoni.kalorie.core.utils

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import kotlin.reflect.KClass
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class DialogViewModelStoreOwnerTest {

    // MARK: - Tests

    @Test
    fun clear_releasesTheViewModelsCreatedThroughTheOwner() {
        val owner = DialogViewModelStoreOwner()
        val viewModel = makeViewModel(owner)
        assertFalse(viewModel.isCleared)

        owner.clear()

        assertTrue(viewModel.isCleared)
    }

    @Test
    fun viewModel_isReusedWhileTheOwnerLives() {
        val owner = DialogViewModelStoreOwner()

        assertSame(makeViewModel(owner), makeViewModel(owner))
    }

    // MARK: - Helpers

    private fun makeViewModel(owner: DialogViewModelStoreOwner): TrackedViewModel {
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: KClass<T>, extras: androidx.lifecycle.viewmodel.CreationExtras): T =
                TrackedViewModel() as T
        }
        return ViewModelProvider.create(owner, factory)[TrackedViewModel::class]
    }

    private class TrackedViewModel : ViewModel() {
        var isCleared = false

        override fun onCleared() {
            isCleared = true
        }
    }
}
