package antoni.kalorie.core.usecases

import antoni.kalorie.core.auth.AuthProviderProtocol
import antoni.kalorie.core.models.FoodItemDomain
import antoni.kalorie.core.models.FoodItemSubmissionDomain
import antoni.kalorie.core.networking.FirestoreDataProviderProtocol

interface UpdateMySubmissionUseCaseProtocol {
    suspend operator fun invoke(id: String, item: FoodItemDomain): FoodItemSubmissionDomain
}

class UpdateMySubmissionUseCase(
    private val dataProvider: FirestoreDataProviderProtocol,
    private val authProvider: AuthProviderProtocol,
) : UpdateMySubmissionUseCaseProtocol {

    // MARK: - Functions

    override suspend fun invoke(id: String, item: FoodItemDomain): FoodItemSubmissionDomain = FoodItemSubmissionWriter.write(
        id = id,
        item = item,
        dataProvider = dataProvider,
        authProvider = authProvider,
    )
}
