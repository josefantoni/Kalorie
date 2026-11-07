package antoni.kalorie.core.usecases

import antoni.kalorie.core.auth.AuthError
import antoni.kalorie.core.auth.AuthProviderProtocol
import antoni.kalorie.core.models.PhotoSuggestionDomain
import antoni.kalorie.core.networking.FirestoreDataProviderProtocol
import antoni.kalorie.core.networking.StorageDataProviderProtocol

interface RejectPhotoSuggestionUseCaseProtocol {
    suspend operator fun invoke(suggestion: PhotoSuggestionDomain)
}

class RejectPhotoSuggestionUseCase(
    dataProvider: FirestoreDataProviderProtocol,
    private val authProvider: AuthProviderProtocol,
    storageProvider: StorageDataProviderProtocol,
) : RejectPhotoSuggestionUseCaseProtocol {

    // MARK: - Properties

    private val remover = PhotoSuggestionRemover(dataProvider, storageProvider)

    // MARK: - Functions

    override suspend fun invoke(suggestion: PhotoSuggestionDomain) {
        if (authProvider.userId == null) throw AuthError.NotAuthenticated
        remover.remove(itemId = suggestion.itemId, userId = suggestion.suggestedBy)
    }
}
