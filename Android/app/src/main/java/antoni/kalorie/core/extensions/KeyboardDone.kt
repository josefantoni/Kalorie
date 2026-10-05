package antoni.kalorie.core.extensions

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import antoni.kalorie.R

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun KeyboardDoneContainer(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    // MARK: - Properties

    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    // MARK: - Body

    Box(modifier = modifier) {
        content()
        if (WindowInsets.isImeVisible) {
            FilledTonalButton(
                onClick = {
                    focusManager.clearFocus()
                    keyboardController?.hide()
                },
                modifier = Modifier.align(Alignment.BottomEnd).padding(horizontal = 16.dp, vertical = 6.dp),
            ) {
                Text(stringResource(R.string.common_button_done))
            }
        }
    }
}
