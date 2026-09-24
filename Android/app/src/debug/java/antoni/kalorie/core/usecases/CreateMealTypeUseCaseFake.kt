package antoni.kalorie.core.usecases

import antoni.kalorie.core.models.MealTypeDomain
import java.util.UUID

class CreateMealTypeUseCaseFake : CreateMealTypeUseCaseProtocol {

    // MARK: - Functions

    override suspend fun invoke(
        name: String,
        startMinutes: Int,
        endMinutes: Int,
        existingMealTypes: List<MealTypeDomain>,
    ): MealTypeDomain =
        MealTypeDomain(id = UUID.randomUUID().toString().uppercase(), name = name, startMinutes = startMinutes, endMinutes = endMinutes)
}
