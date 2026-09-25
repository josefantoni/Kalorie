package antoni.kalorie.core.usecases

import antoni.kalorie.core.auth.AuthProviderProtocol
import antoni.kalorie.core.models.FoodItemDomain
import antoni.kalorie.core.models.FoodItemSubmissionDomain
import antoni.kalorie.core.networking.FirestoreDataProviderProtocol
import java.util.UUID

interface SubmitFoodItemUseCaseProtocol {
    suspend operator fun invoke(item: FoodItemDomain): FoodItemSubmissionDomain
}

class SubmitFoodItemUseCase(
    private val dataProvider: FirestoreDataProviderProtocol,
    private val authProvider: AuthProviderProtocol,
) : SubmitFoodItemUseCaseProtocol {

    // MARK: - Functions

    override suspend fun invoke(item: FoodItemDomain): FoodItemSubmissionDomain = FoodItemSubmissionWriter.write(
        id = UUID.randomUUID().toString().uppercase(),
        item = item,
        dataProvider = dataProvider,
        authProvider = authProvider,
    )
}
