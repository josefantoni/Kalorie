package antoni.kalorie.core.usecases

import antoni.kalorie.core.auth.AuthError
import antoni.kalorie.core.auth.AuthProviderProtocol
import antoni.kalorie.core.networking.FirestoreDataProviderProtocol
import antoni.kalorie.core.utils.Constants

interface DeleteBodyWeightUseCaseProtocol {
    suspend operator fun invoke(dayKey: String)
}

class DeleteBodyWeightUseCase(
    private val dataProvider: FirestoreDataProviderProtocol,
    private val authProvider: AuthProviderProtocol,
) : DeleteBodyWeightUseCaseProtocol {

    // MARK: - Functions

    override suspend fun invoke(dayKey: String) {
        val userId = authProvider.userId ?: throw AuthError.NotAuthenticated
        dataProvider.deleteAsync(id = dayKey, from = Constants.Firestore.bodyWeights(userId))
    }
}
