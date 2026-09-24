package antoni.kalorie.core.usecases

import antoni.kalorie.core.auth.AuthError
import antoni.kalorie.core.auth.AuthProviderProtocol
import antoni.kalorie.core.models.MealTypeDomain
import antoni.kalorie.core.networking.FirestoreDataProviderProtocol
import antoni.kalorie.core.networking.MealTypeDTO
import antoni.kalorie.core.networking.loadAsync
import antoni.kalorie.core.utils.Constants

interface FetchMealTypesUseCaseProtocol {
    suspend operator fun invoke(): List<MealTypeDomain>
}

class FetchMealTypesUseCase(
    private val dataProvider: FirestoreDataProviderProtocol,
    private val authProvider: AuthProviderProtocol,
) : FetchMealTypesUseCaseProtocol {

    // MARK: - Functions

    override suspend fun invoke(): List<MealTypeDomain> {
        val userId = authProvider.userId ?: throw AuthError.NotAuthenticated
        val dtos: List<MealTypeDTO> = dataProvider.loadAsync(Constants.Firestore.mealTypes(userId))
        return dtos
            .map { dto ->
                MealTypeDomain(
                    id = dto.id,
                    name = dto.name,
                    startMinutes = dto.startMinutes,
                    endMinutes = dto.endMinutes,
                )
            }
            .sortedBy { it.startMinutes }
    }
}
