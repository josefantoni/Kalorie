package antoni.kalorie.core.usecases

import antoni.kalorie.core.auth.AuthError
import antoni.kalorie.core.auth.AuthProviderProtocol
import antoni.kalorie.core.models.FoodPortionDomain
import antoni.kalorie.core.models.MyCreatedMealDomain
import antoni.kalorie.core.models.MyCreatedMealIngredientDomain
import antoni.kalorie.core.models.MyCreatedMealValidation
import antoni.kalorie.core.networking.FirestoreDataProviderProtocol
import antoni.kalorie.core.networking.MyCreatedMealDTO
import antoni.kalorie.core.networking.setAsync
import antoni.kalorie.core.utils.Constants
import java.time.Instant
import java.util.UUID

interface CreateMyCreatedMealUseCaseProtocol {
    suspend operator fun invoke(
        name: String,
        ingredients: List<MyCreatedMealIngredientDomain>,
        portions: List<FoodPortionDomain> = emptyList(),
    ): MyCreatedMealDomain
}

class CreateMyCreatedMealUseCase(
    private val dataProvider: FirestoreDataProviderProtocol,
    private val authProvider: AuthProviderProtocol,
) : CreateMyCreatedMealUseCaseProtocol {

    // MARK: - Functions

    override suspend fun invoke(
        name: String,
        ingredients: List<MyCreatedMealIngredientDomain>,
        portions: List<FoodPortionDomain>,
    ): MyCreatedMealDomain {
        val userId = authProvider.userId ?: throw AuthError.NotAuthenticated
        MyCreatedMealValidation.validate(name = name, ingredients = ingredients, portions = portions)?.let { throw it }
        val now = Instant.now()
        val meal = MyCreatedMealDomain(
            id = UUID.randomUUID().toString().uppercase(),
            name = name.trim(),
            ingredients = ingredients,
            createdAt = now,
            updatedAt = now,
            portions = portions,
        )
        dataProvider.setAsync(MyCreatedMealDTO(meal), id = meal.id, inCollection = Constants.Firestore.myCreatedMeals(userId))
        return meal
    }
}
