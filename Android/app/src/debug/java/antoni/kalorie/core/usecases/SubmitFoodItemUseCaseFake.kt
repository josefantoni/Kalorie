package antoni.kalorie.core.usecases

import antoni.kalorie.core.models.FoodItemDomain
import antoni.kalorie.core.models.FoodItemSubmissionDomain
import antoni.kalorie.core.models.FoodItemSubmissionStatus
import java.time.Instant
import java.util.UUID

data class SubmitFoodItemUseCaseFake(
    val errorToThrow: Exception? = null,
) : SubmitFoodItemUseCaseProtocol {

    // MARK: - Functions

    override suspend fun invoke(item: FoodItemDomain): FoodItemSubmissionDomain {
        errorToThrow?.let { throw it }
        return FoodItemSubmissionDomain(
            id = UUID.randomUUID().toString().uppercase(),
            barcode = item.id,
            submittedBy = "test-user-id",
            status = FoodItemSubmissionStatus.PENDING,
            submittedAt = Instant.now(),
            rejectReason = null,
            item = item,
        )
    }
}
