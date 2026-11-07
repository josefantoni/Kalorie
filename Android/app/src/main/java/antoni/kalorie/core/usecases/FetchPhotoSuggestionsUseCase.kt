package antoni.kalorie.core.usecases

import antoni.kalorie.core.auth.AuthError
import antoni.kalorie.core.auth.AuthProviderProtocol
import antoni.kalorie.core.models.PhotoSuggestionDomain
import antoni.kalorie.core.networking.FirestoreDataProviderProtocol
import antoni.kalorie.core.networking.PhotoSuggestionDTO
import antoni.kalorie.core.networking.loadAsync
import antoni.kalorie.core.utils.Constants

interface FetchPhotoSuggestionsUseCaseProtocol {
    suspend operator fun invoke(): List<PhotoSuggestionDomain>
}

class FetchPhotoSuggestionsUseCase(
    private val dataProvider: FirestoreDataProviderProtocol,
    private val authProvider: AuthProviderProtocol,
) : FetchPhotoSuggestionsUseCaseProtocol {

    // MARK: - Functions

    override suspend fun invoke(): List<PhotoSuggestionDomain> {
        if (authProvider.userId == null) throw AuthError.NotAuthenticated
        val dtos: List<PhotoSuggestionDTO> = dataProvider.loadAsync(
            from = Constants.Firestore.PHOTO_SUGGESTIONS,
            orderBy = Constants.Firestore.Field.SUGGESTED_AT,
            descending = false,
            limit = Constants.Firestore.REPORTS_PAGE_LIMIT,
        )
        return dtos.map { it.asDomain() }
    }
}
