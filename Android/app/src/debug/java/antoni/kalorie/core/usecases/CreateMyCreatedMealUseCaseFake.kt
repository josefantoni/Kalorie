package antoni.kalorie.core.usecases

import antoni.kalorie.core.models.FoodPortionDomain
import antoni.kalorie.core.models.MyCreatedMealDomain
import antoni.kalorie.core.models.MyCreatedMealIngredientDomain
import java.time.Instant
import java.util.UUID

data class CreateMyCreatedMealUseCaseFake(
    val shouldThrow: Boolean = false,
) : CreateMyCreatedMealUseCaseProtocol {

    // MARK: - Functions

    override suspend fun invoke(
        name: String,
        ingredients: List<MyCreatedMealIngredientDomain>,
        portions: List<FoodPortionDomain>,
    ): MyCreatedMealDomain {
        if (shouldThrow) throw RuntimeException("unknown")
        return MyCreatedMealDomain(
            id = UUID.randomUUID().toString().uppercase(),
            name = name,
            ingredients = ingredients,
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
            portions = portions,
        )
    }
}
