package antoni.kalorie.core.usecases

import antoni.kalorie.core.auth.AuthError
import antoni.kalorie.core.auth.AuthProviderProtocol
import antoni.kalorie.core.models.FoodItemReportError
import antoni.kalorie.core.models.TicketLocale
import antoni.kalorie.core.networking.FirestoreDataProviderProtocol
import antoni.kalorie.core.networking.FoodItemReportCreateDTO
import antoni.kalorie.core.networking.setAsync
import antoni.kalorie.core.utils.Constants
import antoni.kalorie.features.notifications.UserNotificationsStoreProtocol
import java.time.Instant

interface SubmitFoodItemReportUseCaseProtocol {
    suspend operator fun invoke(barcode: String, reason: String)
}

class SubmitFoodItemReportUseCase(
    private val dataProvider: FirestoreDataProviderProtocol,
    private val authProvider: AuthProviderProtocol,
    private val notificationsStore: UserNotificationsStoreProtocol,
    private val locale: String = TicketLocale.current(),
) : SubmitFoodItemReportUseCaseProtocol {

    // MARK: - Functions

    override suspend fun invoke(barcode: String, reason: String) {
        val userId = authProvider.userId ?: throw AuthError.NotAuthenticated
        val trimmedReason = reason.trim()
        if (trimmedReason.isEmpty()) throw FoodItemReportError.ReasonRequired
        if (trimmedReason.length > Constants.Firestore.REPORT_REASON_MAX_LENGTH) throw FoodItemReportError.ReasonTooLong
        val dto = FoodItemReportCreateDTO(
            barcode = barcode,
            reportedBy = userId,
            reason = trimmedReason,
            reportedAt = Instant.now(),
            locale = locale,
        )
        val ticketId = dataProvider.newDocumentId(Constants.Firestore.FOOD_ITEM_REPORTS)
        dataProvider.setAsync(dto, id = ticketId, inCollection = Constants.Firestore.FOOD_ITEM_REPORTS)
        notificationsStore.mayHaveUnread = true
        notificationsStore.lastSubmittedAt = Instant.now()
    }
}
