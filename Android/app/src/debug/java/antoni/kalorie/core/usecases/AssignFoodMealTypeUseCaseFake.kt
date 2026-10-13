package antoni.kalorie.core.usecases

import antoni.kalorie.core.models.FoodConsumedDomain

class AssignFoodMealTypeUseCaseFake(
    var shouldThrow: Boolean = false,
) : AssignFoodMealTypeUseCaseProtocol {

    // MARK: - Properties

    var beforeResult: (suspend () -> Unit)? = null
    val calls = mutableListOf<Pair<FoodConsumedDomain, String>>()

    // MARK: - Functions

    override suspend fun invoke(food: FoodConsumedDomain, mealTypeId: String) {
        calls += food to mealTypeId
        beforeResult?.let { hook ->
            beforeResult = null
            hook()
        }
        if (shouldThrow) throw RuntimeException("unknown")
    }
}
