package antoni.kalorie.core.utils

import antoni.kalorie.R
import antoni.kalorie.components.FoodItemFormField
import antoni.kalorie.core.nutritionlabelrecognition.NutritionLabelImage
import antoni.kalorie.core.nutritionlabelrecognition.NutritionLabelRecognitionError
import antoni.kalorie.core.nutritionlabelrecognition.RecognizeNutritionLabelUseCaseProtocol
import antoni.kalorie.features.addfoodsheet.FoodItemFormInput
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow

interface NutritionLabelPrefilling {
    val formInput: MutableStateFlow<FoodItemFormInput>
    val recognizedFields: MutableStateFlow<Set<FoodItemFormField>>
    val isRecognizingNutritionLabel: MutableStateFlow<Boolean>
    val isNutritionLabelCameraVisible: MutableStateFlow<Boolean>
    val nutritionLabelCameraHintRes: MutableStateFlow<Int?>

    // MARK: - Functions

    suspend fun recognizeNutritionLabel(
        image: NutritionLabelImage,
        liveBarcode: String?,
        useCase: RecognizeNutritionLabelUseCaseProtocol,
    ): Boolean {
        if (isRecognizingNutritionLabel.value) return false
        isRecognizingNutritionLabel.value = true
        try {
            val recognized = useCase(image)
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
        } catch (error: CancellationException) {
            throw error
        } catch (_: NutritionLabelRecognitionError.NothingRecognized) {
            nutritionLabelCameraHintRes.value = R.string.addFood_nutritionLabel_nothingRecognized
            return false
        } catch (error: Exception) {
            Log.warning(error, Constants.LogCategory.NUTRITION_LABEL_RECOGNITION)
            nutritionLabelCameraHintRes.value = R.string.addFood_nutritionLabel_nothingRecognized
            return false
        } finally {
            isRecognizingNutritionLabel.value = false
        }
    }

    fun onFormFieldEdited(field: FoodItemFormField) {
        recognizedFields.value = recognizedFields.value - field
    }
}
