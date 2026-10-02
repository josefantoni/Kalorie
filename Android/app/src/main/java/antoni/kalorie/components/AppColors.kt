package antoni.kalorie.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import antoni.kalorie.R

object AppColors {

    // MARK: - Brand

    val accent: Color @Composable get() = colorResource(R.color.accent)

    // MARK: - Macros

    val protein: Color @Composable get() = colorResource(R.color.protein)
    val carbs: Color @Composable get() = colorResource(R.color.carbs)
    val fat: Color @Composable get() = colorResource(R.color.fat)

    // MARK: - Status

    val success: Color @Composable get() = colorResource(R.color.success)
    val hint: Color @Composable get() = colorResource(R.color.hint)
    val warning: Color @Composable get() = colorResource(R.color.warning)
    val error: Color @Composable get() = colorResource(R.color.error)
}
