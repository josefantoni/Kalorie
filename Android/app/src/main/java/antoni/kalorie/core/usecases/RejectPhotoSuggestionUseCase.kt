package antoni.kalorie.core.usecases

import antoni.kalorie.core.auth.AuthError
import antoni.kalorie.core.auth.AuthProviderProtocol
import antoni.kalorie.core.models.PhotoSuggestionDomain
import antoni.kalorie.core.models.TicketStatus
import antoni.kalorie.core.networking.FirestoreDataProviderProtocol
import antoni.kalorie.core.networking.StorageDataProviderProtocol
import antoni.kalorie.core.utils.Constants
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

interface RejectPhotoSuggestionUseCaseProtocol {
    suspend operator fun invoke(suggestion: PhotoSuggestionDomain, response: String)
}

class RejectPhotoSuggestionUseCase(
    dataProvider: FirestoreDataProviderProtocol,
    private val authProvider: AuthProviderProtocol,
    storageProvider: StorageDataProviderProtocol,
) : RejectPhotoSuggestionUseCaseProtocol {

    // MARK: - Properties

    private val remover = PhotoSuggestionRemover(storageProvider)
    private val writer = TicketResolutionWriter(dataProvider)

    // MARK: - Functions

    override suspend fun invoke(suggestion: PhotoSuggestionDomain, response: String) {
        if (authProvider.userId == null) throw AuthError.NotAuthenticated
        val trimmedResponse = TicketResolutionWriter.validated(response)
        withContext(NonCancellable) {
            writer.write(
                status = TicketStatus.REJECTED,
                response = trimmedResponse,
                ticketId = suggestion.id,
                inCollection = Constants.Firestore.PHOTO_SUGGESTIONS,
            )
            remover.deleteFileQuietly(suggestion.photoUrl)
        }
    }
}
