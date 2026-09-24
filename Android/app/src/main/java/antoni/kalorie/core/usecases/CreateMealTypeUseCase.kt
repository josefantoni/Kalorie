package antoni.kalorie.core.usecases

import antoni.kalorie.core.auth.AuthError
import antoni.kalorie.core.auth.AuthProviderProtocol
import antoni.kalorie.core.models.MealTypeDomain
import antoni.kalorie.core.networking.FirestoreDataProviderProtocol
import antoni.kalorie.core.networking.MealTypeDTO
import antoni.kalorie.core.networking.setAsync
import antoni.kalorie.core.utils.Constants
import antoni.kalorie.features.mealtypesheet.CreateMealTypeError
import antoni.kalorie.mealkit.MIN_MEAL_WINDOW_MINUTES
import antoni.kalorie.mealkit.isMealWindowLongEnough
import antoni.kalorie.mealkit.mealWindowsOverlap
import java.util.UUID

interface CreateMealTypeUseCaseProtocol {
    suspend operator fun invoke(
        name: String,
        startMinutes: Int,
        endMinutes: Int,
        existingMealTypes: List<MealTypeDomain>,
    ): MealTypeDomain
}

class CreateMealTypeUseCase(
    private val dataProvider: FirestoreDataProviderProtocol,
    private val authProvider: AuthProviderProtocol,
) : CreateMealTypeUseCaseProtocol {

    // MARK: - Functions

    override suspend fun invoke(
        name: String,
        startMinutes: Int,
        endMinutes: Int,
        existingMealTypes: List<MealTypeDomain>,
    ): MealTypeDomain {
        val name = name.trim()
        if (name.isEmpty()) throw CreateMealTypeError.EmptyName
        val comparableName = name.lowercase()
        if (existingMealTypes.any { it.name.trim().lowercase() == comparableName }) {
            throw CreateMealTypeError.DuplicateName
        }
        if (!isMealWindowLongEnough(startMinutes, endMinutes, MIN_MEAL_WINDOW_MINUTES)) {
            throw CreateMealTypeError.DurationTooShort
        }
        if (existingMealTypes.any { mealWindowsOverlap(startMinutes, endMinutes, it.startMinutes, it.endMinutes) }) {
            throw CreateMealTypeError.TimeConflict
        }
        val userId = authProvider.userId ?: throw AuthError.NotAuthenticated
        val newId = UUID.randomUUID().toString().uppercase()
        val dto = MealTypeDTO(id = newId, name = name, startMinutes = startMinutes, endMinutes = endMinutes)
        dataProvider.setAsync(dto, id = newId, inCollection = Constants.Firestore.mealTypes(userId))
        return MealTypeDomain(id = newId, name = name, startMinutes = startMinutes, endMinutes = endMinutes)
    }
}
