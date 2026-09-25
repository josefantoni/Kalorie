package antoni.kalorie.core.usecases

import antoni.kalorie.core.auth.AuthError
import antoni.kalorie.core.auth.AuthProviderProtocol
import antoni.kalorie.core.models.MyCreatedMealDomain
import antoni.kalorie.core.networking.FirestoreDataProviderProtocol
import antoni.kalorie.core.networking.MyCreatedMealDTO
import antoni.kalorie.core.networking.loadAsync
import antoni.kalorie.core.utils.Constants

interface FetchMyCreatedMealsUseCaseProtocol {
    suspend operator fun invoke(): List<MyCreatedMealDomain>
}

class FetchMyCreatedMealsUseCase(
    private val dataProvider: FirestoreDataProviderProtocol,
    private val authProvider: AuthProviderProtocol,
) : FetchMyCreatedMealsUseCaseProtocol {

    // MARK: - Functions

    override suspend fun invoke(): List<MyCreatedMealDomain> {
        val userId = authProvider.userId ?: throw AuthError.NotAuthenticated
        val dtos: List<MyCreatedMealDTO> = dataProvider.loadAsync(
            from = Constants.Firestore.myCreatedMeals(userId),
            orderBy = "updated_at",
            descending = true,
            limit = 50,
        )
        return dtos.map { it.asDomain() }
    }
}
