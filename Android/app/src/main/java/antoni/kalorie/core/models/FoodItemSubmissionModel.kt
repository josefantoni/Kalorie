package antoni.kalorie.core.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant

@Serializable
enum class FoodItemSubmissionStatus(val wireValue: String) {
    @SerialName("pending")
    PENDING("pending"),

    @SerialName("rejected")
    REJECTED("rejected"),
}

data class FoodItemSubmissionDomain(
    val id: String,
    val barcode: String?,
    val submittedBy: String,
    val status: FoodItemSubmissionStatus,
    val submittedAt: Instant,
    val rejectReason: String?,
    val item: FoodItemDomain,
)

sealed class FoodItemSubmissionError : Exception() {
    data object InvalidCode : FoodItemSubmissionError()
    data object InvalidName : FoodItemSubmissionError()
    data object InvalidCalories : FoodItemSubmissionError()
    data object InvalidAlcoholByVolume : FoodItemSubmissionError()
    data class InvalidPortion(val error: FoodPortionError) : FoodItemSubmissionError()
    data object ItemAlreadyExists : FoodItemSubmissionError()
    data object PhotoMissing : FoodItemSubmissionError()
    data object PhotoUploadFailed : FoodItemSubmissionError()

    // MARK: - Functions

    companion object {
        fun from(validationError: FoodItemValidationError): FoodItemSubmissionError = validationError.mapped(
            invalidCode = InvalidCode,
            invalidName = InvalidName,
            invalidCalories = InvalidCalories,
            invalidAlcoholByVolume = InvalidAlcoholByVolume,
        ) { InvalidPortion(it) }
    }
}
