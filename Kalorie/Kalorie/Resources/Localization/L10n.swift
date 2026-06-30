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
    }

    enum Dashboard {
        static var buttonMealLayout: String { String(localized: "dashboard_button_mealLayout") }
        static var sectionUnassignedFoods: String { String(localized: "dashboard_section_unassignedFoods") }
        static var emptyTitle: String { String(localized: "dashboard_empty_title") }
        static var emptyDescription: String { String(localized: "dashboard_empty_description") }
        static var emptyAddFood: String { String(localized: "dashboard_empty_addFood") }
    }

    enum AddFood {
        static var searchPlaceholder: String { String(localized: "addFood_search_placeholder") }
        static var cameraPermissionAlert: String { String(localized: "addFood_camera_permissionAlert") }
        static var sectionSearchResults: String { String(localized: "addFood_section_searchResults") }
        static var sectionNewItem: String { String(localized: "addFood_section_newItem") }
        static var fieldBarcodeTitle: String { String(localized: "addFood_field_barcode_title") }
        static var fieldBarcodePlaceholder: String { String(localized: "addFood_field_barcode_placeholder") }
        static var fieldNameTitle: String { String(localized: "addFood_field_name_title") }
        static var fieldNamePlaceholder: String { String(localized: "addFood_field_name_placeholder") }
        static var fieldWeight: String { String(localized: "addFood_field_weight") }
        static var fieldCaloriesPer100g: String { String(localized: "addFood_field_caloriesPer100g") }
        static var fieldProtein: String { String(localized: "addFood_field_protein") }
        static var fieldCarbs: String { String(localized: "addFood_field_carbs") }
        static var fieldCarbsSugar: String { String(localized: "addFood_field_carbsSugar") }
        static var fieldFat: String { String(localized: "addFood_field_fat") }
        static var fieldFatUnsaturated: String { String(localized: "addFood_field_fatUnsaturated") }
        static var fieldSalt: String { String(localized: "addFood_field_salt") }
        static var buttonAdd: String { String(localized: "addFood_button_add") }
        static var errorInvalidCode: String { String(localized: "addFood_error_invalidCode") }
        static var errorInvalidName: String { String(localized: "addFood_error_invalidName") }
        static var errorInvalidCalories: String { String(localized: "addFood_error_invalidCalories") }
        static var errorInvalidWeight: String { String(localized: "addFood_error_invalidWeight") }
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
        static var errorDeleteError: String { String(localized: "mealTypeSheet_error_deleteError") }
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
