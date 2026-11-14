package antoni.kalorie.core.usecases

import antoni.kalorie.core.models.TicketStatus

data class ResolveFoodItemReportUseCaseFake(
    val errorToThrow: Exception? = null,
) : ResolveFoodItemReportUseCaseProtocol {

    // MARK: - Functions

    override suspend fun invoke(ticketId: String, status: TicketStatus, response: String) {
        errorToThrow?.let { throw it }
    }
}
