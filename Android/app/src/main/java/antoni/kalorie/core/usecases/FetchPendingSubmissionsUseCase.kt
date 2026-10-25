package antoni.kalorie.core.usecases

import antoni.kalorie.core.auth.AuthError
import antoni.kalorie.core.auth.AuthProviderProtocol
import antoni.kalorie.core.models.FoodItemSubmissionDomain
import antoni.kalorie.core.models.FoodItemSubmissionStatus
import antoni.kalorie.core.networking.FirestoreDataProviderProtocol
import antoni.kalorie.core.utils.Constants

interface FetchPendingSubmissionsUseCaseProtocol {
    suspend operator fun invoke(): List<FoodItemSubmissionDomain>
}

class FetchPendingSubmissionsUseCase(
    private val dataProvider: FirestoreDataProviderProtocol,
    private val authProvider: AuthProviderProtocol,
) : FetchPendingSubmissionsUseCaseProtocol {

    // MARK: - Functions

    override suspend fun invoke(): List<FoodItemSubmissionDomain> {
        if (authProvider.userId == null) throw AuthError.NotAuthenticated
        return FoodItemSubmissionFetcher.fetch(
            field = Constants.Firestore.Field.STATUS,
            isEqualTo = FoodItemSubmissionStatus.PENDING.wireValue,
            dataProvider = dataProvider,
        )
    }
}
