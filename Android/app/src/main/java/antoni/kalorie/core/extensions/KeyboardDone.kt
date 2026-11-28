package antoni.kalorie.core.extensions

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import antoni.kalorie.R

val LocalKeyboardDoneClearance = compositionLocalOf { 0.dp }

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun KeyboardDoneContainer(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    // MARK: - Properties

    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val density = LocalDensity.current
    var buttonHeight by remember { mutableStateOf(0.dp) }

    // MARK: - Body

    Box(modifier = modifier) {
        val isImeVisible = WindowInsets.isImeVisible
        CompositionLocalProvider(LocalKeyboardDoneClearance provides if (isImeVisible) buttonHeight else 0.dp) {
            content()
        }
        if (isImeVisible) {
            FilledTonalButton(
                onClick = {
                    focusManager.clearFocus()
                    keyboardController?.hide()
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .onSizeChanged { buttonHeight = with(density) { it.height.toDp() } }
                    .padding(horizontal = 16.dp, vertical = 6.dp),
            ) {
                Text(stringResource(R.string.common_button_done))
            }
        }
    }
}

// MARK: - Functions

@Composable
fun Modifier.keepAboveKeyboardDone(): Modifier {
    val clearance = with(LocalDensity.current) { LocalKeyboardDoneClearance.current.toPx() }
    val requester = remember { BringIntoViewRequester() }
    var size by remember { mutableStateOf(IntSize.Zero) }
    var isFocused by remember { mutableStateOf(false) }
    if (isFocused && clearance > 0f) {
        val keyboardHeight = WindowInsets.ime.getBottom(LocalDensity.current)
        LaunchedEffect(keyboardHeight, clearance, size) {
            requester.bringIntoView(Rect(0f, 0f, size.width.toFloat(), size.height + clearance))
        }
    }
    return bringIntoViewRequester(requester)
        .onSizeChanged { size = it }
        .onFocusChanged { isFocused = it.hasFocus }
}
