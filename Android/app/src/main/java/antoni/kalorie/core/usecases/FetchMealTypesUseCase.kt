package antoni.kalorie.core.usecases

import antoni.kalorie.core.auth.AuthError
import antoni.kalorie.core.auth.AuthProviderProtocol
import antoni.kalorie.core.models.MealTypeDomain
import antoni.kalorie.core.networking.FirestoreDataProviderProtocol
import antoni.kalorie.core.networking.MealTypeDTO
import antoni.kalorie.core.networking.loadAsync
import antoni.kalorie.core.utils.Constants
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

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
        val zone = ZoneId.systemDefault()
        val dayStart = Instant.now().atZone(zone).toLocalDate().atStartOfDay(zone).toInstant()
        return dtos
            .map { dto ->
                MealTypeDomain(
                    id = dto.id,
                    name = dto.name,
                    startTime = dayStart.plus(Duration.ofMinutes(dto.startMinutes.toLong())),
                    endTime = dayStart.plus(Duration.ofMinutes(dto.endMinutes.toLong())),
                )
            }
            .sortedBy { it.startTime }
    }
}
