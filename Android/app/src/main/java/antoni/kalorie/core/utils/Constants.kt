package antoni.kalorie.core.utils

import antoni.kalorie.BuildConfig
import kotlin.time.Duration.Companion.milliseconds

object Constants {

    object Support {
        const val EMAIL = "kaloriepodpora@gmail.com"
        const val PRIVACY_POLICY_URL = "https://kalorie-bf11c.web.app/privacy"
    }

    object LogCategory {
        const val FIRESTORE = "firestore"
        const val AUTH = "auth"
        const val DASHBOARD = "dashboard"
        const val SETTINGS = "settings"
        const val FOOD_QUANTITY = "foodQuantity"
        const val ADD_FOOD_SHEET = "addFoodSheet"
        const val FAVOURITES = "favourites"
        const val MY_CREATED_MEAL = "myCreatedMeal"
        const val FOOD_ITEM_REPORT = "foodItemReport"
        const val ACCOUNT = "account"
        const val EXPORT = "export"
        const val MODERATION = "moderation"
        const val NUTRITION_LABEL_RECOGNITION = "nutritionLabelRecognition"
        const val STORAGE = "storage"
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

    object Storage {
        const val SUBMISSION_PHOTOS_FOLDER = "submissionPhotos"
        const val CATALOG_PHOTOS_FOLDER = "catalogPhotos"
        const val PHOTO_CONTENT_TYPE = "image/jpeg"
        const val MAX_DOWNLOAD_BYTES = 5L * 1024 * 1024
        const val PHOTO_CACHE_CONTROL = "public, max-age=31536000, immutable"
    }

    object Search {
        const val MINIMUM_QUERY_LENGTH = 2
        const val FREQUENCY_ENTRY_LIMIT = 300
    }

    object Firestore {
        const val BATCH_WRITE_LIMIT = 500
        const val IN_QUERY_LIMIT = 30
        const val USERS = "users"
        const val FOOD_ITEMS = "foodItems"
        const val FOOD_ITEM_REPORTS = "foodItemReports"
        const val FOOD_ITEM_SUBMISSIONS = "foodItemSubmissions"
        const val REPORT_REASON_MAX_LENGTH = 500
        const val REPORTS_PAGE_LIMIT = 50
        const val FOOD_FREQUENCY_DOCUMENT_ID = "foodFrequency"

        fun mealTypes(userId: String): String = "users/$userId/mealTypes"
        fun foodConsumed(userId: String): String = "users/$userId/foodConsumed"
        fun favouriteFoods(userId: String): String = "users/$userId/favouriteFoods"
        fun foodItemPortions(userId: String): String = "users/$userId/foodItemPortions"
        fun myCreatedMeals(userId: String): String = "users/$userId/myCreatedMeals"
        fun stats(userId: String): String = "users/$userId/stats"
    }
}
