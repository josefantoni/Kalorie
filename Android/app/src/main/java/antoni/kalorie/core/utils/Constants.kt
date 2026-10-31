package antoni.kalorie.core.utils

import antoni.kalorie.BuildConfig
import java.util.Locale
import kotlin.time.Duration.Companion.milliseconds

object Constants {

    object Support {
        const val EMAIL = "kaloriepodpora@gmail.com"
        const val PRIVACY_POLICY_URL = "https://kalorie-bf11c.web.app/privacy"
    }

    object Store {
        const val PLAY_STORE_URL = "https://play.google.com/store/apps/details?id=antoni.kalorie"
    }

    object DateFormat {
        const val MONTH_CACHE_KEY = "yyyy-MM"
        const val DAY_CACHE_KEY = "yyyy-MM-dd"
    }

    object CacheFolder {
        const val EXPORTS = "exports"
        const val PHOTOS = "photos"
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
        const val SUPPORT = "support"
        const val APP_VERSION = "appVersion"
    }

    object Tips {
        val IS_ENABLED = false
        const val SMALL_PRODUCT_ID = "tip_small"
        const val MEDIUM_PRODUCT_ID = "tip_medium"
        const val LARGE_PRODUCT_ID = "tip_large"
        val productIds = listOf(SMALL_PRODUCT_ID, MEDIUM_PRODUCT_ID, LARGE_PRODUCT_ID)
    }

    object Auth {
        const val RECENT_LOGIN_THRESHOLD_SECONDS = 4 * 60L
        const val MAINTAINER_CLAIM_CACHE_TTL_SECONDS = 5 * 60L
        const val APPLE_PROVIDER_ID = "apple.com"
        const val PENDING_MERGE_SNAPSHOT_FILE_NAME = "pending-merge.json"
        const val MAINTAINER_CLAIM = "maintainer"
        const val SIGN_IN_SPOTLIGHT_PREFERENCES = "signInSpotlight"
        const val SIGN_IN_SPOTLIGHT_LAST_SHOWN_AT_KEY = "signInSpotlightLastShownAt"

        object ErrorCode {
            const val USER_MISMATCH = "ERROR_USER_MISMATCH"
            const val PROVIDER_ALREADY_LINKED = "ERROR_PROVIDER_ALREADY_LINKED"
            const val CREDENTIAL_ALREADY_IN_USE = "ERROR_CREDENTIAL_ALREADY_IN_USE"
            const val EMAIL_ALREADY_IN_USE = "ERROR_EMAIL_ALREADY_IN_USE"
            const val WEB_CONTEXT_CANCELED = "ERROR_WEB_CONTEXT_CANCELED"
        }
    }

    object AddFood {
        const val CONTRIBUTION_HINT_PREFERENCES = "contributionHint"
        const val CONTRIBUTION_HINT_HIDDEN_AT_KEY = "contributionHintHiddenAt"
        const val CONTRIBUTION_HINT_HIDE_COUNT_KEY = "contributionHintHideCount"
    }

    object OpenFoodFacts {
        const val HOST = "world.openfoodfacts.org"
        const val SEARCH_PAGE_SIZE = 20
        const val SERVING_PORTION_NAME = "1 porce"
        const val GRAMS_UNIT = "g"
        const val MILLILITRES_UNIT = "ml"
        const val MAX_SERVING_QUANTITY = 5000.0
        const val PER_HUNDRED_AMOUNT = 100.0
        const val SERVING_QUANTITY_WHITESPACE = " \t\n\r"
        const val PRODUCT_FIELDS = "code,product_name,product_name_cs,product_name_en,nutriments,image_front_url,serving_quantity,serving_quantity_unit"
        const val REQUEST_TIMEOUT_MILLIS = 10_000
        const val MAX_ATTEMPTS = 3
        val RETRY_DELAY = 500.milliseconds
        val USER_AGENT: String = "Kalorie-Android/${BuildConfig.VERSION_NAME}"
    }

    object Ciqual {
        const val DATASET_URL = "https://zenodo.org/records/4770600"
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

    object BodyWeight {
        const val DEFAULT_KILOGRAMS = 70.0
        const val KILOGRAMS_PER_POUND = 0.45359237
        const val FIRST_YEAR = 2026
        val FIRST_DAY_KEY = "%04d-01-01".format(Locale.ROOT, FIRST_YEAR)
    }

    object Firestore {
        const val BATCH_WRITE_LIMIT = 500
        const val IN_QUERY_LIMIT = 30
        const val USERS = "users"
        const val FOOD_ITEMS = "foodItems"
        const val FOOD_ITEM_REPORTS = "foodItemReports"
        const val CONFIG = "config"
        const val APP_CONFIG_DOCUMENT_ID = "app"
        const val FOOD_ITEM_SUBMISSIONS = "foodItemSubmissions"
        const val REPORT_REASON_MAX_LENGTH = 500
        const val REPORTS_PAGE_LIMIT = 50
        const val FOOD_FREQUENCY_DOCUMENT_ID = "foodFrequency"
        const val APP_SETTINGS_DOCUMENT_ID = "app"
        const val BODY_WEIGHT_RECENT_LIMIT = 3
        const val BODY_WEIGHT_LATER_LIMIT = 2

        object Field {
            const val CZ_NAME_FOLDED = "cz_name_folded"
            const val ENG_NAME_FOLDED = "eng_name_folded"
            const val CZ_NAME_SEARCH_TERMS = "cz_name_search_terms"
            const val ENG_NAME_SEARCH_TERMS = "eng_name_search_terms"
            const val SUBMITTED_BY = "submitted_by"
            const val SUBMITTED_AT = "submitted_at"
            const val REPORTED_BY = "reported_by"
            const val REPORTED_AT = "reported_at"
            const val FAVOURITED_AT = "favourited_at"
            const val UPDATED_AT = "updated_at"
            const val STATUS = "status"
            const val DATE = "date"
            const val ID = "id"
        }

        object FoodFrequency {
            const val ENTRIES = "entries"
            const val ITEM = "item"
            const val LAST_LOGGED_AT = "last_logged_at"
            const val COUNT = "count"
        }

        fun mealTypes(userId: String): String = "users/$userId/mealTypes"
        fun foodConsumed(userId: String): String = "users/$userId/foodConsumed"
        fun favouriteFoods(userId: String): String = "users/$userId/favouriteFoods"
        fun foodItemPortions(userId: String): String = "users/$userId/foodItemPortions"
        fun myCreatedMeals(userId: String): String = "users/$userId/myCreatedMeals"
        fun bodyWeights(userId: String): String = "users/$userId/bodyWeights"
        fun settings(userId: String): String = "users/$userId/settings"
        fun stats(userId: String): String = "users/$userId/stats"
        fun mergedAnonymousAccounts(userId: String): String = "users/$userId/mergedAnonymousAccounts"
    }
}
