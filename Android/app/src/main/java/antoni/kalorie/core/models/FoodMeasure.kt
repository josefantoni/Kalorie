package antoni.kalorie.core.models

import androidx.annotation.StringRes
import antoni.kalorie.R

enum class FoodMeasure(val rawValue: String) {
    GRAMS("grams"),
    MILLILITRES("millilitres");

    // MARK: - Properties

    @get:StringRes
    val unitSymbolRes: Int
        get() = when (this) {
            GRAMS -> R.string.common_unit_grams
            MILLILITRES -> R.string.common_unit_millilitres
        }

    companion object {
        fun fromRawValue(rawValue: String): FoodMeasure? = entries.firstOrNull { it.rawValue == rawValue }
    }
}
