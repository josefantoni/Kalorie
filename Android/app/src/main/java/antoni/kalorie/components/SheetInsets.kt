package antoni.kalorie.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection

// ModalBottomSheet reserves the navigation bar by default, which clips scrolling content above the gesture bar.
// The sheet keeps only the status bar and the keyboard; each scroll container applies the bottom inset itself.
@Composable
fun sheetContentWindowInsets(): WindowInsets = WindowInsets.statusBars.union(WindowInsets.ime)

@Composable
fun PaddingValues.excludingBottom(): PaddingValues {
    val layoutDirection = LocalLayoutDirection.current
    return PaddingValues(
        start = calculateStartPadding(layoutDirection),
        top = calculateTopPadding(),
        end = calculateEndPadding(layoutDirection),
    )
}

fun PaddingValues.bottomOnly(): PaddingValues = PaddingValues(bottom = calculateBottomPadding())

@Composable
fun Modifier.sheetBottomBar(): Modifier = background(MaterialTheme.colorScheme.background).navigationBarsPadding()
