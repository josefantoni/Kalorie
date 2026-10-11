package antoni.kalorie.core.extensions

import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp

// Matches the iOS xLarge cap (19 pt body over the 17 pt default).
private const val MAX_FONT_SCALE = 1.15f

// Compose dialogs, popups and bottom sheets open their own window and build their Density from the
// Activity's resources, so they ignore a LocalDensity override. Capping the configuration reaches all of them.
fun Context.withDynamicTypeLimit(): Context {
    // MARK: - Properties

    val configuration = Configuration(resources.configuration)

    // MARK: - Body

    if (configuration.fontScale <= MAX_FONT_SCALE) return this
    configuration.fontScale = MAX_FONT_SCALE
    return createConfigurationContext(configuration)
}

@Composable
fun Dp.scaledWithText(): Dp = with(LocalDensity.current) { value.sp.toDp() }
