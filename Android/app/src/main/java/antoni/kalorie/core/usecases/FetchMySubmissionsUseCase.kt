package antoni.kalorie.core.usecases

import antoni.kalorie.core.auth.AuthError
import antoni.kalorie.core.auth.AuthProviderProtocol
import antoni.kalorie.core.models.FoodItemSubmissionDomain
import antoni.kalorie.core.networking.FirestoreDataProviderProtocol

interface FetchMySubmissionsUseCaseProtocol {
    suspend operator fun invoke(): List<FoodItemSubmissionDomain>
}

class FetchMySubmissionsUseCase(
    private val dataProvider: FirestoreDataProviderProtocol,
    private val authProvider: AuthProviderProtocol,
) : FetchMySubmissionsUseCaseProtocol {

    // MARK: - Functions

    override suspend fun invoke(): List<FoodItemSubmissionDomain> {
        val userId = authProvider.userId ?: throw AuthError.NotAuthenticated
        return FoodItemSubmissionFetcher.fetch(field = "submitted_by", isEqualTo = userId, dataProvider = dataProvider)
    }
}
