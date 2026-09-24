package antoni.kalorie.core.usecases

import antoni.kalorie.core.auth.AuthError
import antoni.kalorie.core.auth.AuthProviderProtocol
import antoni.kalorie.core.models.FoodConsumedDomain
import antoni.kalorie.core.networking.FirestoreDataProviderProtocol
import antoni.kalorie.core.networking.FoodConsumedDTO
import antoni.kalorie.core.networking.loadAsync
import antoni.kalorie.core.utils.Constants
import java.time.Instant
import java.time.ZoneId

interface FetchFoodsConsumedForMonthUseCaseProtocol {
    suspend operator fun invoke(month: Instant): List<FoodConsumedDomain>
}

class FetchFoodsConsumedForMonthUseCase(
    private val dataProvider: FirestoreDataProviderProtocol,
    private val authProvider: AuthProviderProtocol,
) : FetchFoodsConsumedForMonthUseCaseProtocol {

    // MARK: - Functions

    override suspend fun invoke(month: Instant): List<FoodConsumedDomain> {
        val userId = authProvider.userId ?: throw AuthError.NotAuthenticated
        val zone = ZoneId.systemDefault()
        val startOfMonth = month.atZone(zone).toLocalDate().withDayOfMonth(1).atStartOfDay(zone)
        val startOfNextMonth = startOfMonth.plusMonths(1)
        val dtos: List<FoodConsumedDTO> = dataProvider.loadAsync(
            from = Constants.Firestore.foodConsumed(userId),
            field = "date",
            isGreaterThanOrEqualTo = startOfMonth.toEpochSecond().toDouble(),
            isLessThan = startOfNextMonth.toEpochSecond().toDouble(),
        )
        return dtos.map { it.asDomain() }
    }
}
