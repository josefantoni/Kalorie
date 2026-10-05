package antoni.kalorie.core.extensions

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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

    Column(modifier = modifier) {
        Box(modifier = Modifier.weight(1f).fillMaxWidth(), content = content)
        if (WindowInsets.isImeVisible) {
            Row(
                modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                FilledTonalButton(
                    onClick = {
                        focusManager.clearFocus()
                        keyboardController?.hide()
                    },
                ) {
                    Text(stringResource(R.string.common_button_done))
                }
            }
        }
    }
}
