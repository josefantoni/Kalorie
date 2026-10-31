//
//  Constants.swift
//  Kalorie
//
//  Created by Josef Antoni on 30.06.2026.
//

import Foundation

enum Constants {

    enum Time {
        static let secondsPerDay: TimeInterval = 24 * 60 * 60
    }

    enum Auth {
        static let appleProviderId = "apple.com"
        static let googleProviderId = "google.com"
        static let pendingMergeSnapshotFileName = "pending-merge.json"
        static let recentLoginThreshold: TimeInterval = 4 * 60
        static let maintainerClaimCacheTTL: TimeInterval = 5 * 60
        static let maintainerClaim = "maintainer"
    }

    enum AddFood {
        static let contributionHintHiddenAtKey = "contributionHintHiddenAt"
        static let contributionHintHideCountKey = "contributionHintHideCount"
    }

    enum OpenFoodFacts {
        static let host = "world.openfoodfacts.org"
        static let baseURL = URL(string: "https://\(host)")
        static let requestTimeout: TimeInterval = 10
        static let maxAttempts = 3
        static let retryDelay: Duration = .milliseconds(500)
        static let searchPageSize = 20
        static let servingPortionName = "1 porce"
        static let gramsUnit = "g"
        static let millilitresUnit = "ml"
        static let perHundredAmount = 100.0
        static let productFields = "code,product_name,product_name_cs,product_name_en,nutriments,image_front_url,serving_quantity,serving_quantity_unit"
        static var userAgent: String {
            let version = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "0"
            return "Kalorie-iOS/\(version)"
        }
    }

    enum Ciqual {
        static let datasetURL = URL(string: "https://zenodo.org/records/4770600")
    }

    enum Storage {
        static let submissionPhotosFolder = "submissionPhotos"
        static let catalogPhotosFolder = "catalogPhotos"
        static let photoContentType = "image/jpeg"
        static let maxDownloadBytes: Int64 = 5 * 1024 * 1024
        static let photoCacheControl = "public, max-age=31536000, immutable"
        static let imageCacheMemoryBytes = 20 * 1024 * 1024
        static let imageCacheDiskBytes = 100 * 1024 * 1024
        static let photoStoreMaxBytes = 50 * 1024 * 1024
    }

    enum Support {
        static let email = "kaloriepodpora@gmail.com"
        static let privacyPolicyURL = URL(string: "https://kalorie-bf11c.web.app/privacy")
    }

    enum Store {
        static let appStoreURL = URL(string: "https://apps.apple.com/app/id6823155404")
    }

    enum Tips {
        static let isEnabled = false
        static let mediumProductId = "tip_medium"
        static let largeProductId = "tip_large"
        static let productIds = ["tip_small", mediumProductId, largeProductId]
    }

    enum DateFormat {
        static let monthCacheKey = "yyyy-MM"
        static let dayCacheKey = "yyyy-MM-dd"
    }

    enum LogCategory {
        static let subsystem = "antoni.Kalorie"
        static let defaultCategory = "app"
        static let firestore = "firestore"
        static let storage = "storage"
        static let auth = "auth"
        static let account = "account"
        static let favourites = "favourites"
        static let foodQuantity = "foodQuantity"
        static let settings = "settings"
        static let addFoodSheet = "addFoodSheet"
        static let dashboard = "dashboard"
        static let myCreatedMeal = "myCreatedMeal"
        static let moderation = "moderation"
        static let foodItemReport = "foodItemReport"
        static let nutritionLabelRecognition = "nutritionLabelRecognition"
        static let export = "export"
        static let support = "support"
        static let appVersion = "appVersion"
    }

    enum Search {
        static let minimumQueryLength = 2
        static let frequencyEntryLimit = 300
    }

    enum ImageSize {
        static let basic: CGFloat = 20
        static let medium: CGFloat = 30
        static let extraLarge: CGFloat = 60
    }

    enum BodyWeight {
        static let defaultKilograms = 70.0
        static let kilogramsPerPound = 0.45359237
        static let firstYear = 2026
        static let firstDayKey = String(format: "%04d-01-01", firstYear)
    }

    enum Firestore {
        static let foodItems = "foodItems"
        static let foodItemSubmissions = "foodItemSubmissions"
        static let foodItemReports = "foodItemReports"
        static let config = "config"
        static let appConfigDocumentId = "app"
        static let users = "users"
        static let batchWriteLimit = 500
        static let inQueryLimit = 30
        static let reportsPageLimit = 50
        static let reportReasonMaxLength = 500
        static let foodFrequencyDocumentId = "foodFrequency"
        static let appSettingsDocumentId = "app"
        static let bodyWeightRecentLimit = 3
        static let bodyWeightLaterLimit = 2

        enum Field {
            static let czNameFolded = "cz_name_folded"
            static let engNameFolded = "eng_name_folded"
            static let czNameSearchTerms = "cz_name_search_terms"
            static let engNameSearchTerms = "eng_name_search_terms"
            static let submittedBy = "submitted_by"
            static let submittedAt = "submitted_at"
            static let reportedBy = "reported_by"
            static let reportedAt = "reported_at"
            static let favouritedAt = "favourited_at"
            static let updatedAt = "updated_at"
            static let status = "status"
            static let date = "date"
            static let id = "id"
        }

        enum FoodFrequency {
            static let entries = "entries"
            static let item = "item"
            static let lastLoggedAt = "last_logged_at"
            static let count = "count"
        }
        static func mealTypes(userId: String) -> String { "users/\(userId)/mealTypes" }
        static func foodConsumed(userId: String) -> String { "users/\(userId)/foodConsumed" }
        static func favouriteFoods(userId: String) -> String { "users/\(userId)/favouriteFoods" }
        static func myCreatedMeals(userId: String) -> String { "users/\(userId)/myCreatedMeals" }
        static func foodItemPortions(userId: String) -> String { "users/\(userId)/foodItemPortions" }
        static func stats(userId: String) -> String { "users/\(userId)/stats" }
        static func bodyWeights(userId: String) -> String { "users/\(userId)/bodyWeights" }
        static func settings(userId: String) -> String { "users/\(userId)/settings" }
        static func mergedAnonymousAccounts(userId: String) -> String { "users/\(userId)/mergedAnonymousAccounts" }
    }
}
