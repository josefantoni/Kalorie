//
//  L10n.swift
//  Kalorie
//
//  Created by Josef Antoni on 29.06.2026.
//

import Foundation

enum L10n {
    enum Common {
        static var ok: String { String(localized: "common_ok") }
        static var errorUnknown: String { String(localized: "common_error_unknown") }
        static var buttonFavourite: String { String(localized: "common_button_favourite") }
    }

    enum Auth {
        static var errorSignInFailed: String { String(localized: "auth_error_signInFailed") }
        static var buttonRetry: String { String(localized: "auth_button_retry") }
        static var mergingMessage: String { String(localized: "auth_mergingMessage") }
    }

    enum Account {
        static var navigationTitle: String { String(localized: "account_navigationTitle") }
        static var anonymousDescription: String { String(localized: "account_anonymous_description") }
        static var buttonSignOut: String { String(localized: "account_button_signOut") }
        static var buttonSignInWithGoogle: String { String(localized: "account_button_signInWithGoogle") }
        static var buttonDeleteAccount: String { String(localized: "account_button_deleteAccount") }
        static var errorSignInFailed: String { String(localized: "account_error_signInFailed") }
        static var errorAccountExistsWithApple: String { String(localized: "account_error_accountExistsWithApple") }
        static var errorSignOutFailed: String { String(localized: "account_error_signOutFailed") }
        static var errorDeleteFailed: String { String(localized: "account_error_deleteFailed") }
        static var errorDeleteRequiresRecentLogin: String { String(localized: "account_error_deleteRequiresRecentLogin") }
        static var signedInDefaultName: String { String(localized: "account_signedIn_defaultName") }
        static var alertDeleteConfirmTitle: String { String(localized: "account_alert_deleteConfirmTitle") }
        static var alertDeleteConfirmMessage: String { String(localized: "account_alert_deleteConfirmMessage") }
    }

    enum Dashboard {
        static var navigationTitle: String { String(localized: "dashboard_navigation_title") }
        static var buttonMealLayout: String { String(localized: "dashboard_button_mealLayout") }
        static var sectionUnassignedFoods: String { String(localized: "dashboard_section_unassignedFoods") }
        static var emptyTitle: String { String(localized: "dashboard_empty_title") }
        static var emptyDescription: String { String(localized: "dashboard_empty_description") }
        static var emptyTitlePast: String { String(localized: "dashboard_empty_title_past") }
        static var emptyDescriptionPast: String { String(localized: "dashboard_empty_description_past") }
        static var emptyTitleFuture: String { String(localized: "dashboard_empty_title_future") }
        static var emptyDescriptionFuture: String { String(localized: "dashboard_empty_description_future") }
        static var emptyAddFood: String { String(localized: "dashboard_empty_addFood") }
    }

    enum AddFood {
        static var searchPlaceholder: String { String(localized: "addFood_search_placeholder") }
        static var cameraPermissionAlert: String { String(localized: "addFood_camera_permissionAlert") }
        static var sectionSearchResults: String { String(localized: "addFood_section_searchResults") }
        static var sectionExternalResults: String { String(localized: "addFood_section_externalResults") }
        static var sectionFavourites: String { String(localized: "addFood_section_favourites") }
        static var sectionNewItem: String { String(localized: "addFood_section_newItem") }
        static var fieldBarcodeTitle: String { String(localized: "addFood_field_barcode_title") }
        static var fieldBarcodePlaceholder: String { String(localized: "addFood_field_barcode_placeholder") }
        static var fieldNameTitle: String { String(localized: "addFood_field_name_title") }
        static var fieldNamePlaceholder: String { String(localized: "addFood_field_name_placeholder") }
        static var fieldWeight: String { String(localized: "addFood_field_weight") }
        static var fieldEnergyKJ: String { String(localized: "addFood_field_energyKJ") }
        static var fieldCaloriesPer100g: String { String(localized: "addFood_field_caloriesPer100g") }
        static var fieldProtein: String { String(localized: "addFood_field_protein") }
        static var fieldCarbs: String { String(localized: "addFood_field_carbs") }
        static var fieldCarbsSugar: String { String(localized: "addFood_field_carbsSugar") }
        static var fieldFiber: String { String(localized: "addFood_field_fiber") }
        static var fieldFat: String { String(localized: "addFood_field_fat") }
        static var fieldFatSaturated: String { String(localized: "addFood_field_fatSaturated") }
        static var fieldFatUnsaturated: String { String(localized: "addFood_field_fatUnsaturated") }
        static var fieldSalt: String { String(localized: "addFood_field_salt") }
        static var buttonAdd: String { String(localized: "addFood_button_add") }
        static var errorInvalidCode: String { String(localized: "addFood_error_invalidCode") }
        static var errorInvalidName: String { String(localized: "addFood_error_invalidName") }
        static var errorInvalidCalories: String { String(localized: "addFood_error_invalidCalories") }
        static var errorInvalidWeight: String { String(localized: "addFood_error_invalidWeight") }
        static var errorBarcodeNotFound: String { String(localized: "addFood_error_barcodeNotFound") }
        static var errorItemAlreadyExists: String { String(localized: "addFood_error_itemAlreadyExists") }
        static var errorLoadFailed: String { String(localized: "addFood_error_loadFailed") }
        static var errorFavouriteFailed: String { String(localized: "addFood_error_favouriteFailed") }
    }

    enum FoodConsumedDetail {
        static var labelTime: String { String(localized: "foodConsumedDetail_label_time") }
        static var buttonSave: String { String(localized: "foodConsumedDetail_button_save") }
    }

    enum FoodQuantity {
        static var sectionNutrition: String { String(localized: "foodQuantity_section_nutrition") }
        static var unitHundredGrams: String { String(localized: "foodQuantity_unit_hundredGrams") }
        static var unitGrams: String { String(localized: "foodQuantity_unit_grams") }
        static var inputHundredGrams: String { String(localized: "foodQuantity_input_portions") }
        static var inputGrams: String { String(localized: "foodQuantity_input_grams") }
        static var buttonAdd: String { String(localized: "foodQuantity_button_add") }
        static var calories: String { String(localized: "foodQuantity_macro_calories") }
        static var protein: String { String(localized: "foodQuantity_macro_protein") }
        static var carbs: String { String(localized: "foodQuantity_macro_carbs") }
        static var fat: String { String(localized: "foodQuantity_macro_fat") }
        static var fiber: String { String(localized: "foodQuantity_macro_fiber") }
        static var errorInvalidQuantity: String { String(localized: "foodQuantity_error_invalidQuantity") }
    }

    enum MealTypeSheet {
        static var sectionMealLayout: String { String(localized: "mealTypeSheet_section_mealLayout") }
        static var fieldNewMealPlaceholder: String { String(localized: "mealTypeSheet_field_newMeal_placeholder") }
        static var datePickerFrom: String { String(localized: "mealTypeSheet_datePicker_from") }
        static var datePickerTo: String { String(localized: "mealTypeSheet_datePicker_to") }
        static var buttonCreate: String { String(localized: "mealTypeSheet_button_create") }
        static var errorEmptyName: String { String(localized: "mealTypeSheet_error_emptyName") }
        static var errorDuplicateName: String { String(localized: "mealTypeSheet_error_duplicateName") }
        static var errorTimeConflict: String { String(localized: "mealTypeSheet_error_timeConflict") }
        static var errorDurationTooShort: String { String(localized: "mealTypeSheet_error_durationTooShort") }
        static var errorDeleteError: String { String(localized: "mealTypeSheet_error_deleteError") }
        static var errorLastMealType: String { String(localized: "mealTypeSheet_error_lastMealType") }
        static var errorUnexpected: String { String(localized: "mealTypeSheet_error_unexpected") }
    }

    enum DefaultMeals {
        static var breakfast: String { String(localized: "defaultMeals_breakfast") }
        static var secondBreakfast: String { String(localized: "defaultMeals_secondBreakfast") }
        static var lunch: String { String(localized: "defaultMeals_lunch") }
        static var snack: String { String(localized: "defaultMeals_snack") }
        static var dinner: String { String(localized: "defaultMeals_dinner") }
    }
}
