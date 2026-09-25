package antoni.kalorie.core.usecases

import antoni.kalorie.core.models.FoodItemDomain
import antoni.kalorie.core.models.FoodItemSubmissionDomain
import antoni.kalorie.core.models.FoodItemSubmissionStatus
import java.time.Instant

data class UpdateMySubmissionUseCaseFake(
    val errorToThrow: Exception? = null,
) : UpdateMySubmissionUseCaseProtocol {

    // MARK: - Functions

    override suspend fun invoke(id: String, item: FoodItemDomain): FoodItemSubmissionDomain {
        errorToThrow?.let { throw it }
        return FoodItemSubmissionDomain(
            id = id,
            barcode = item.id,
            submittedBy = "test-user-id",
            status = FoodItemSubmissionStatus.PENDING,
            submittedAt = Instant.now(),
            rejectReason = null,
            item = item,
        )
    }
}
