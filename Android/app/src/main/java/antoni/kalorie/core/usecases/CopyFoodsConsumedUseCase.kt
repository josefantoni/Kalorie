package antoni.kalorie.core.usecases

import antoni.kalorie.core.auth.AuthError
import antoni.kalorie.core.auth.AuthProviderProtocol
import antoni.kalorie.core.models.FoodConsumedDomain
import antoni.kalorie.core.models.MealTypeDomain
import antoni.kalorie.core.models.copyTargetMinutes
import antoni.kalorie.core.networking.FirestoreDataProviderProtocol
import antoni.kalorie.core.networking.FoodConsumedDTO
import antoni.kalorie.core.networking.batchSetAsync
import antoni.kalorie.core.utils.Constants
import antoni.kalorie.core.utils.isSameDay
import antoni.kalorie.core.utils.minutesSinceMidnight
import antoni.kalorie.core.utils.zoned
import java.time.Instant
import java.util.UUID

sealed class CopyFoodsConsumedError : Exception() {
    data object MealTypeNotFound : CopyFoodsConsumedError()
}

interface CopyFoodsConsumedUseCaseProtocol {
    suspend operator fun invoke(foods: List<FoodConsumedDomain>, toDay: Instant, mealTypeId: String, mealTypes: List<MealTypeDomain>)
}

class CopyFoodsConsumedUseCase(
    private val dataProvider: FirestoreDataProviderProtocol,
    private val authProvider: AuthProviderProtocol,
    private val now: () -> Instant = { Instant.now() },
) : CopyFoodsConsumedUseCaseProtocol {

    // MARK: - Functions

    override suspend fun invoke(foods: List<FoodConsumedDomain>, toDay: Instant, mealTypeId: String, mealTypes: List<MealTypeDomain>) {
        val userId = authProvider.userId ?: throw AuthError.NotAuthenticated
        val target = targetDate(day = toDay, mealTypeId = mealTypeId, mealTypes = mealTypes)
        val items = foods.mapIndexed { index, food ->
            val dto = FoodConsumedDTO(
                food = food,
                mealTypeId = mealTypeId,
                id = UUID.randomUUID().toString().uppercase(),
                date = target.plusSeconds(index.toLong()),
            )
            dto to dto.id
        }
        dataProvider.batchSetAsync(items, inCollection = Constants.Firestore.foodConsumed(userId))
    }

    // MARK: - Private

    private fun targetDate(day: Instant, mealTypeId: String, mealTypes: List<MealTypeDomain>): Instant {
        val current = now()
        val minutes = mealTypes.copyTargetMinutes(
            nowMinutes = if (day.isSameDay(current)) current.minutesSinceMidnight() else null,
            targetId = mealTypeId,
        ) ?: throw CopyFoodsConsumedError.MealTypeNotFound
        val zoned = day.zoned()
        return zoned.toLocalDate().atTime(minutes / 60, minutes % 60).atZone(zoned.zone).toInstant()
    }
}
