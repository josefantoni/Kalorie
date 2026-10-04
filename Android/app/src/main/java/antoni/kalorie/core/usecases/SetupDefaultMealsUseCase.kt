package antoni.kalorie.core.usecases

import antoni.kalorie.core.auth.AuthError
import antoni.kalorie.core.auth.AuthProviderProtocol
import antoni.kalorie.core.models.MealTypeDomain
import antoni.kalorie.core.networking.FirestoreDataProviderProtocol
import antoni.kalorie.core.networking.MealTypeDTO
import antoni.kalorie.core.networking.batchSetAsync
import antoni.kalorie.core.utils.Constants
import antoni.kalorie.core.utils.DefaultMeals
import antoni.kalorie.core.utils.StringProvider
import java.util.UUID

interface SetupDefaultMealsUseCaseProtocol {
    suspend operator fun invoke(): List<MealTypeDomain>
}

class SetupDefaultMealsUseCase(
    private val dataProvider: FirestoreDataProviderProtocol,
    private val authProvider: AuthProviderProtocol,
    private val stringProvider: StringProvider,
) : SetupDefaultMealsUseCaseProtocol {

    // MARK: - Functions

    override suspend fun invoke(): List<MealTypeDomain> {
        val userId = authProvider.userId ?: throw AuthError.NotAuthenticated
        val dtos = mutableListOf<Pair<MealTypeDTO, String>>()
        val domains = mutableListOf<MealTypeDomain>()

        for ((index, defaultKey) in DefaultMeals.keys.withIndex()) {
            val mealName = DefaultMeals.name(defaultKey, stringProvider) ?: defaultKey
            val startMinutes = DEFAULT_BOUNDARIES[index]
            val endMinutes = DEFAULT_BOUNDARIES[index + 1]
            val id = UUID.randomUUID().toString().uppercase()
            dtos += MealTypeDTO(
                id = id,
                name = mealName,
                startMinutes = startMinutes,
                endMinutes = endMinutes,
                defaultKey = defaultKey,
            ) to id
            domains += MealTypeDomain(
                id = id,
                name = mealName,
                startMinutes = startMinutes,
                endMinutes = endMinutes,
                defaultKey = defaultKey,
            )
        }

        dataProvider.batchSetAsync(dtos, inCollection = Constants.Firestore.mealTypes(userId))
        return domains
    }

    private companion object {
        val DEFAULT_BOUNDARIES = listOf(5 * 60, 8 * 60 + 30, 11 * 60, 14 * 60 + 30, 17 * 60, 20 * 60)
    }
}
