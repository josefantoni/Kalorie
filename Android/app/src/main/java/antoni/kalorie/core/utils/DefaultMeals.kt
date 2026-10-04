package antoni.kalorie.core.utils

import antoni.kalorie.R

object DefaultMeals {
    val keys = listOf("breakfast", "secondBreakfast", "lunch", "snack", "dinner")

    // MARK: - Functions

    fun name(key: String, strings: StringProvider): String? {
        val id = when (key) {
            "breakfast" -> R.string.defaultMeals_breakfast
            "secondBreakfast" -> R.string.defaultMeals_secondBreakfast
            "lunch" -> R.string.defaultMeals_lunch
            "snack" -> R.string.defaultMeals_snack
            "dinner" -> R.string.defaultMeals_dinner
            else -> return null
        }
        return strings.getString(id)
    }
}
