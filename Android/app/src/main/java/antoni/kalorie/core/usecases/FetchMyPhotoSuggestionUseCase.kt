package antoni.kalorie.core.usecases

import antoni.kalorie.core.auth.AuthError
import antoni.kalorie.core.auth.AuthProviderProtocol
import antoni.kalorie.core.networking.FirestoreDataProviderProtocol
import antoni.kalorie.core.networking.PhotoSuggestionDTO
import antoni.kalorie.core.networking.loadAsync
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
        val dtos: List<PhotoSuggestionDTO> = dataProvider.loadAsync(
            from = Constants.Firestore.PHOTO_SUGGESTIONS,
            whereEqualTo = listOf(
                Constants.Firestore.Field.SUGGESTED_BY to userId,
                Constants.Firestore.Field.ITEM_ID to itemId,
                Constants.Firestore.Field.STATUS to Constants.Firestore.PENDING_STATUS,
            ),
            limit = 1,
        )
        return dtos.isNotEmpty()
    }
}
