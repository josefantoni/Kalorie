package antoni.kalorie.core.usecases

import antoni.kalorie.core.auth.AuthError
import antoni.kalorie.core.auth.AuthProviderProtocol
import antoni.kalorie.core.models.MealTypeDomain
import antoni.kalorie.core.networking.FirestoreDataProviderProtocol
import antoni.kalorie.core.networking.MealTypeDTO
import antoni.kalorie.core.networking.batchSetAsync
import antoni.kalorie.core.utils.Constants
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
        var startMinutes = DEFAULT_START_HOUR * 60
        var endMinutes = startMinutes + DEFAULT_WINDOW_HOURS * 60
        val dtos = mutableListOf<Pair<MealTypeDTO, String>>()
        val domains = mutableListOf<MealTypeDomain>()

        for (mealName in mealNames) {
            val id = UUID.randomUUID().toString().uppercase()
            dtos += MealTypeDTO(
                id = id,
                name = mealName,
                startMinutes = startMinutes,
                endMinutes = endMinutes,
            ) to id
            domains += MealTypeDomain(id = id, name = mealName, startMinutes = startMinutes, endMinutes = endMinutes)
            startMinutes = endMinutes
            endMinutes = startMinutes + DEFAULT_WINDOW_HOURS * 60
        }

        dataProvider.batchSetAsync(dtos, inCollection = Constants.Firestore.mealTypes(userId))
        return domains
    }

    private companion object {
        const val DEFAULT_START_HOUR = 5
        const val DEFAULT_WINDOW_HOURS = 3
    }
}
