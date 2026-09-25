package antoni.kalorie.core.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner

class DialogViewModelStoreOwner : ViewModelStoreOwner {

    // MARK: - Properties

    override val viewModelStore = ViewModelStore()

    // MARK: - Functions

    fun clear() {
        viewModelStore.clear()
    }
}

// A view model created in a dialog's configurator lives only as long as that dialog stays in the
// composition, instead of piling up in the Activity's store until the Activity is destroyed.
@Composable
fun rememberDialogViewModelStoreOwner(): ViewModelStoreOwner {
    val owner = remember { DialogViewModelStoreOwner() }
    DisposableEffect(owner) {
        onDispose { owner.clear() }
    }
    return owner
}
