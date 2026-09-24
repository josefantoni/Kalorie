package antoni.kalorie.core.usecases

import antoni.kalorie.core.auth.AuthError
import antoni.kalorie.core.auth.AuthProviderProtocol
import antoni.kalorie.core.models.MealTypeDomain
import antoni.kalorie.core.networking.FirestoreDataProviderProtocol
import antoni.kalorie.core.networking.MealTypeDTO
import antoni.kalorie.core.networking.batchSetAsync
import antoni.kalorie.core.utils.Constants
import antoni.kalorie.core.utils.minutesSinceMidnight
import antoni.kalorie.core.utils.withAddedHours
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

interface SetupDefaultMealsUseCaseProtocol {
    suspend operator fun invoke(): List<MealTypeDomain>
}

class SetupDefaultMealsUseCase(
    private val dataProvider: FirestoreDataProviderProtocol,
    private val authProvider: AuthProviderProtocol,
    private val mealNames: List<String>,
) : SetupDefaultMealsUseCaseProtocol {

    // MARK: - Functions

    override suspend fun invoke(): List<MealTypeDomain> {
        val userId = authProvider.userId ?: throw AuthError.NotAuthenticated
        val zone = ZoneId.systemDefault()
        var startTime = Instant.now().atZone(zone).toLocalDate().atTime(DEFAULT_START_HOUR, 0).atZone(zone).toInstant()
        var endTime = startTime.withAddedHours(hours = DEFAULT_WINDOW_HOURS)
        val dtos = mutableListOf<Pair<MealTypeDTO, String>>()
        val domains = mutableListOf<MealTypeDomain>()

        for (mealName in mealNames) {
            val id = UUID.randomUUID().toString().uppercase()
            dtos += MealTypeDTO(
                id = id,
                name = mealName,
                startMinutes = startTime.minutesSinceMidnight(),
                endMinutes = endTime.minutesSinceMidnight(),
            ) to id
            domains += MealTypeDomain(id = id, name = mealName, startTime = startTime, endTime = endTime)
            startTime = endTime
            endTime = startTime.withAddedHours(hours = DEFAULT_WINDOW_HOURS)
        }

        dataProvider.batchSetAsync(dtos, inCollection = Constants.Firestore.mealTypes(userId))
        return domains
    }

    private companion object {
        const val DEFAULT_START_HOUR = 5
        const val DEFAULT_WINDOW_HOURS = 3.0
    }
}
