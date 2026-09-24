package antoni.kalorie.core.usecases

import antoni.kalorie.core.models.MealTypeDomain

data class SetupDefaultMealsUseCaseFake(
    val stubbedTypes: List<MealTypeDomain> = emptyList(),
) : SetupDefaultMealsUseCaseProtocol {

    // MARK: - Functions

    override suspend fun invoke(): List<MealTypeDomain> = stubbedTypes
}
