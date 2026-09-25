package antoni.kalorie.core.usecases

import antoni.kalorie.core.auth.AuthError
import antoni.kalorie.core.auth.AuthProviderProtocol
import antoni.kalorie.core.networking.FirestoreDataProviderProtocol
import antoni.kalorie.core.utils.Constants

interface DeleteMySubmissionUseCaseProtocol {
    suspend operator fun invoke(id: String)
}

class DeleteMySubmissionUseCase(
    private val dataProvider: FirestoreDataProviderProtocol,
    private val authProvider: AuthProviderProtocol,
) : DeleteMySubmissionUseCaseProtocol {

    // MARK: - Functions

    override suspend fun invoke(id: String) {
        if (authProvider.userId == null) throw AuthError.NotAuthenticated
        dataProvider.deleteAsync(id = id, from = Constants.Firestore.FOOD_ITEM_SUBMISSIONS)
    }
}
