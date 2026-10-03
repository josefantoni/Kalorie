package antoni.kalorie.core.utils

import antoni.kalorie.R
import antoni.kalorie.components.FoodItemFormField
import antoni.kalorie.core.nutritionlabelrecognition.NutritionLabelReading
import antoni.kalorie.features.addfoodsheet.FoodItemFormInput
import kotlinx.coroutines.flow.MutableStateFlow

interface NutritionLabelPrefilling {
    val formInput: MutableStateFlow<FoodItemFormInput>
    val recognizedFields: MutableStateFlow<Set<FoodItemFormField>>
    val isNutritionLabelCameraVisible: MutableStateFlow<Boolean>
    val nutritionLabelCameraHintRes: MutableStateFlow<Int?>

    // MARK: - Functions

    fun applyRecognizedNutritionLabel(recognized: NutritionLabelReading, liveBarcode: String?): Boolean {
        val reading = recognized.copy(scannedCode = recognized.scannedCode ?: liveBarcode)
        if (reading.recognizedFields.isEmpty()) {
            nutritionLabelCameraHintRes.value = R.string.addFood_nutritionLabel_nothingRecognized
            return false
        }
        val (applied, appliedFields) = formInput.value.applying(reading, recognizedFields.value)
        formInput.value = applied
        recognizedFields.value = recognizedFields.value + appliedFields
        nutritionLabelCameraHintRes.value = null
        isNutritionLabelCameraVisible.value = false
        return true
    }

    fun onFormFieldEdited(field: FoodItemFormField) {
        recognizedFields.value = recognizedFields.value - field
    }
}
