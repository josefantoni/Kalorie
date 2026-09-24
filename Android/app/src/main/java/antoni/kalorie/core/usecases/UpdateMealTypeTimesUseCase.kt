package antoni.kalorie.core.usecases

import antoni.kalorie.core.auth.AuthError
import antoni.kalorie.core.auth.AuthProviderProtocol
import antoni.kalorie.core.models.MealTypeDomain
import antoni.kalorie.core.networking.FirestoreDataProviderProtocol
import antoni.kalorie.core.networking.MealTypeDTO
import antoni.kalorie.core.networking.batchSetAsync
import antoni.kalorie.core.utils.Constants

interface UpdateMealTypeTimesUseCaseProtocol {
    suspend operator fun invoke(mealTypes: List<MealTypeDomain>)
}

class UpdateMealTypeTimesUseCase(
    private val dataProvider: FirestoreDataProviderProtocol,
    private val authProvider: AuthProviderProtocol,
) : UpdateMealTypeTimesUseCaseProtocol {

    // MARK: - Functions

    override suspend fun invoke(mealTypes: List<MealTypeDomain>) {
        val userId = authProvider.userId ?: throw AuthError.NotAuthenticated
        val dtos = mealTypes.map { mealType ->
            MealTypeDTO(
                id = mealType.id,
                name = mealType.name,
                startMinutes = mealType.startMinutes,
                endMinutes = mealType.endMinutes,
            ) to mealType.id
        }
        dataProvider.batchSetAsync(dtos, inCollection = Constants.Firestore.mealTypes(userId))
    }
}
