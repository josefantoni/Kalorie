package antoni.kalorie.core.usecases

import antoni.kalorie.core.auth.AuthError
import antoni.kalorie.core.auth.AuthProviderProtocol
import antoni.kalorie.core.models.FoodItemReportDomain
import antoni.kalorie.core.models.FoodItemReportError
import antoni.kalorie.core.networking.FirestoreDataProviderProtocol
import antoni.kalorie.core.networking.FoodItemReportDTO
import antoni.kalorie.core.networking.setAsync
import antoni.kalorie.core.utils.Constants
import java.time.Instant

interface SubmitFoodItemReportUseCaseProtocol {
    suspend operator fun invoke(barcode: String, reason: String)
}

class SubmitFoodItemReportUseCase(
    private val dataProvider: FirestoreDataProviderProtocol,
    private val authProvider: AuthProviderProtocol,
) : SubmitFoodItemReportUseCaseProtocol {

    // MARK: - Functions

    override suspend fun invoke(barcode: String, reason: String) {
        val userId = authProvider.userId ?: throw AuthError.NotAuthenticated
        val trimmedReason = reason.trim()
        if (trimmedReason.isEmpty()) throw FoodItemReportError.ReasonRequired
        if (trimmedReason.length > Constants.Firestore.REPORT_REASON_MAX_LENGTH) throw FoodItemReportError.ReasonTooLong
        val dto = FoodItemReportDTO(barcode = barcode, reportedBy = userId, reason = trimmedReason, reportedAt = Instant.now())
        dataProvider.setAsync(
            dto,
            id = FoodItemReportDomain.id(barcode = barcode, userId = userId),
            inCollection = Constants.Firestore.FOOD_ITEM_REPORTS,
        )
    }
}
