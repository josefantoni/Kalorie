package antoni.kalorie.core.usecases

import antoni.kalorie.core.auth.AuthError
import antoni.kalorie.core.auth.AuthProviderProtocol
import antoni.kalorie.core.models.PhotoSuggestionDomain
import antoni.kalorie.core.networking.FirestoreDataProviderProtocol
import antoni.kalorie.core.networking.PhotoSuggestionDTO
import antoni.kalorie.core.networking.loadFromServerAsync
import antoni.kalorie.core.utils.Constants

interface FetchMyPhotoSuggestionUseCaseProtocol {
    suspend operator fun invoke(itemId: String): Boolean
}

class FetchMyPhotoSuggestionUseCase(
    private val dataProvider: FirestoreDataProviderProtocol,
    private val authProvider: AuthProviderProtocol,
) : FetchMyPhotoSuggestionUseCaseProtocol {

    // MARK: - Functions

    override suspend fun invoke(itemId: String): Boolean {
        val userId = authProvider.userId ?: throw AuthError.NotAuthenticated
        val dto: PhotoSuggestionDTO? = dataProvider.loadFromServerAsync(
            id = PhotoSuggestionDomain.id(itemId, userId),
            from = Constants.Firestore.PHOTO_SUGGESTIONS,
        )
        return dto != null
    }
}
