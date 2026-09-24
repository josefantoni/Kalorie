package antoni.kalorie.core.utils

object Constants {

    object LogCategory {
        const val FIRESTORE = "firestore"
        const val AUTH = "auth"
        const val DASHBOARD = "dashboard"
        const val MEAL_TYPE_SHEET = "mealTypeSheet"
    }

    object Firestore {
        const val BATCH_WRITE_LIMIT = 500

        fun mealTypes(userId: String): String = "users/$userId/mealTypes"
        fun foodConsumed(userId: String): String = "users/$userId/foodConsumed"
    }
}
