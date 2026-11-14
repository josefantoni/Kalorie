package antoni.kalorie.core.usecases

import antoni.kalorie.core.models.TicketStatus
import antoni.kalorie.core.networking.FirestoreDataProviderProtocol
import antoni.kalorie.core.networking.TicketResolutionDTO
import antoni.kalorie.core.networking.updateAsync
import antoni.kalorie.core.utils.Constants
import java.time.Instant

sealed class TicketResolutionError : Exception() {
    data object ResponseRequired : TicketResolutionError()
    data object ResponseTooLong : TicketResolutionError()
}

class TicketResolutionWriter(
    private val dataProvider: FirestoreDataProviderProtocol,
) {

    // MARK: - Functions

    suspend fun write(status: TicketStatus, response: String, ticketId: String, inCollection: String) {
        dataProvider.updateAsync(
            TicketResolutionDTO(status = status, response = response, resolvedAt = Instant.now()),
            id = ticketId,
            inCollection = inCollection,
        )
    }

    companion object {
        fun validated(response: String): String {
            val trimmed = response.trim()
            if (trimmed.isEmpty()) throw TicketResolutionError.ResponseRequired
            if (trimmed.length > Constants.Firestore.RESPONSE_MAX_LENGTH) throw TicketResolutionError.ResponseTooLong
            return trimmed
        }
    }
}
