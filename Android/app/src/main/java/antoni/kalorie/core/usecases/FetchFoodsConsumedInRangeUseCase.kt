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

interface FetchFoodsConsumedInRangeUseCaseProtocol {
    suspend operator fun invoke(from: Instant, to: Instant): List<FoodConsumedDomain>
}

class FetchFoodsConsumedInRangeUseCase(
    private val dataProvider: FirestoreDataProviderProtocol,
    private val authProvider: AuthProviderProtocol,
    private val zone: ZoneId = ZoneId.systemDefault(),
) : FetchFoodsConsumedInRangeUseCaseProtocol {

    // MARK: - Functions

    override suspend fun invoke(from: Instant, to: Instant): List<FoodConsumedDomain> {
        val userId = authProvider.userId ?: throw AuthError.NotAuthenticated
        val start = from.atZone(zone).toLocalDate().atStartOfDay(zone)
        val end = to.atZone(zone).toLocalDate().plusDays(1).atStartOfDay(zone)
        val dtos: List<FoodConsumedDTO> = dataProvider.loadAsync(
            from = Constants.Firestore.foodConsumed(userId),
            field = "date",
            isGreaterThanOrEqualTo = start.toEpochSecond().toDouble(),
            isLessThan = end.toEpochSecond().toDouble(),
        )
        return dtos.map { it.asDomain() }
    }
}
