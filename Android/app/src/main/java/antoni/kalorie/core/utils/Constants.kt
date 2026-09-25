package antoni.kalorie.core.utils

import antoni.kalorie.BuildConfig
import kotlin.time.Duration.Companion.milliseconds

object Constants {

    object LogCategory {
        const val FIRESTORE = "firestore"
        const val AUTH = "auth"
        const val DASHBOARD = "dashboard"
        const val MEAL_TYPE_SHEET = "mealTypeSheet"
        const val FOOD_QUANTITY = "foodQuantity"
        const val ADD_FOOD_SHEET = "addFoodSheet"
        const val FAVOURITES = "favourites"
        const val MY_CREATED_MEAL = "myCreatedMeal"
        const val FOOD_ITEM_REPORT = "foodItemReport"
        const val ACCOUNT = "account"
    }

    object Auth {
        const val RECENT_LOGIN_THRESHOLD_SECONDS = 4 * 60L
        const val MAINTAINER_CLAIM_CACHE_TTL_SECONDS = 5 * 60L
    }

    object OpenFoodFacts {
        const val HOST = "world.openfoodfacts.org"
        const val REQUEST_TIMEOUT_MILLIS = 10_000
        const val MAX_ATTEMPTS = 3
        val RETRY_DELAY = 500.milliseconds
        val USER_AGENT: String = "Kalorie-Android/${BuildConfig.VERSION_NAME}"
    }

    object Firestore {
        const val BATCH_WRITE_LIMIT = 500
        const val IN_QUERY_LIMIT = 30
        const val USERS = "users"
        const val FOOD_ITEMS = "foodItems"
        const val FOOD_ITEM_REPORTS = "foodItemReports"
        const val FOOD_ITEM_SUBMISSIONS = "foodItemSubmissions"
        const val REPORT_REASON_MAX_LENGTH = 500

        fun mealTypes(userId: String): String = "users/$userId/mealTypes"
        fun foodConsumed(userId: String): String = "users/$userId/foodConsumed"
        fun favouriteFoods(userId: String): String = "users/$userId/favouriteFoods"
        fun foodItemPortions(userId: String): String = "users/$userId/foodItemPortions"
        fun myCreatedMeals(userId: String): String = "users/$userId/myCreatedMeals"
    }
}
