package antoni.kalorie.core.usecases

import antoni.kalorie.core.auth.AuthError
import antoni.kalorie.core.auth.AuthProviderProtocol
import antoni.kalorie.core.models.TicketStatus
import antoni.kalorie.core.networking.FirestoreDataProviderProtocol
import antoni.kalorie.core.utils.Constants

interface ResolveFoodItemReportUseCaseProtocol {
    suspend operator fun invoke(ticketId: String, status: TicketStatus, response: String)
}

class ResolveFoodItemReportUseCase(
    dataProvider: FirestoreDataProviderProtocol,
    private val authProvider: AuthProviderProtocol,
) : ResolveFoodItemReportUseCaseProtocol {

    // MARK: - Properties

    private val writer = TicketResolutionWriter(dataProvider)

    // MARK: - Functions

    override suspend fun invoke(ticketId: String, status: TicketStatus, response: String) {
        if (authProvider.userId == null) throw AuthError.NotAuthenticated
        val trimmedResponse = TicketResolutionWriter.validated(response)
        writer.write(
            status = status,
            response = trimmedResponse,
            ticketId = ticketId,
            inCollection = Constants.Firestore.FOOD_ITEM_REPORTS,
        )
    }
}
