package antoni.kalorie.core.usecases

import antoni.kalorie.core.auth.AuthError
import antoni.kalorie.core.auth.AuthProviderProtocol
import antoni.kalorie.core.models.MyCreatedMealDomain
import antoni.kalorie.core.models.MyCreatedMealValidation
import antoni.kalorie.core.networking.FirestoreDataProviderProtocol
import antoni.kalorie.core.networking.MyCreatedMealDTO
import antoni.kalorie.core.networking.setAsync
import antoni.kalorie.core.utils.Constants
import java.time.Instant

interface UpdateMyCreatedMealUseCaseProtocol {
    suspend operator fun invoke(meal: MyCreatedMealDomain)
}

class UpdateMyCreatedMealUseCase(
    private val dataProvider: FirestoreDataProviderProtocol,
    private val authProvider: AuthProviderProtocol,
) : UpdateMyCreatedMealUseCaseProtocol {

    // MARK: - Functions

    override suspend fun invoke(meal: MyCreatedMealDomain) {
        val userId = authProvider.userId ?: throw AuthError.NotAuthenticated
        MyCreatedMealValidation.validate(name = meal.name, ingredients = meal.ingredients, portions = meal.portions)?.let { throw it }
        val updated = meal.copy(name = meal.name.trim(), updatedAt = Instant.now())
        dataProvider.setAsync(MyCreatedMealDTO(updated), id = updated.id, inCollection = Constants.Firestore.myCreatedMeals(userId))
    }
}
