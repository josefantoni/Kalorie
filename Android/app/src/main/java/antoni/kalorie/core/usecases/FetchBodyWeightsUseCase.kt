package antoni.kalorie.core.usecases

import antoni.kalorie.core.auth.AuthError
import antoni.kalorie.core.auth.AuthProviderProtocol
import antoni.kalorie.core.models.BodyWeightDayKey
import antoni.kalorie.core.models.BodyWeightDomain
import antoni.kalorie.core.networking.BodyWeightDTO
import antoni.kalorie.core.networking.FirestoreDataProviderProtocol
import antoni.kalorie.core.networking.loadGreaterThanAsync
import antoni.kalorie.core.networking.loadLessThanOrEqualAsync
import antoni.kalorie.core.networking.loadRangeAsync
import antoni.kalorie.core.utils.Constants
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Locale

interface FetchBodyWeightsUseCaseProtocol {
    suspend fun upTo(day: Instant): List<BodyWeightDomain>
    suspend fun after(day: Instant): List<BodyWeightDomain>
    suspend fun between(from: Instant, to: Instant): List<BodyWeightDomain>
    suspend fun inYear(year: Int): List<BodyWeightDomain>
}

class FetchBodyWeightsUseCase(
    private val dataProvider: FirestoreDataProviderProtocol,
    private val authProvider: AuthProviderProtocol,
) : FetchBodyWeightsUseCaseProtocol {

    // MARK: - Functions

    override suspend fun upTo(day: Instant): List<BodyWeightDomain> = load(upTo = day, limit = Constants.Firestore.BODY_WEIGHT_RECENT_LIMIT)

    override suspend fun after(day: Instant): List<BodyWeightDomain> {
        val userId = authProvider.userId ?: throw AuthError.NotAuthenticated
        val weights: List<BodyWeightDTO> = dataProvider.loadGreaterThanAsync(
            from = Constants.Firestore.bodyWeights(userId),
            field = Constants.Firestore.Field.ID,
            value = BodyWeightDayKey.make(day),
            limit = Constants.Firestore.BODY_WEIGHT_LATER_LIMIT,
        )
        return weights.map { it.asDomain() }
    }

    override suspend fun between(from: Instant, to: Instant): List<BodyWeightDomain> {
        val zone = ZoneId.systemDefault()
        val dayCount = ChronoUnit.DAYS.between(from.atZone(zone).toLocalDate(), to.atZone(zone).toLocalDate()).toInt() + 1
        return load(upTo = to, limit = dayCount + 1)
    }

    override suspend fun inYear(year: Int): List<BodyWeightDomain> {
        val userId = authProvider.userId ?: throw AuthError.NotAuthenticated
        val weights: List<BodyWeightDTO> = dataProvider.loadRangeAsync(
            from = Constants.Firestore.bodyWeights(userId),
            field = Constants.Firestore.Field.ID,
            lowerBound = "%04d-01-01".format(Locale.ROOT, year),
            upperBound = "%04d-12-31".format(Locale.ROOT, year),
        )
        return weights.map { it.asDomain() }
    }

    // MARK: - Private

    private suspend fun load(upTo: Instant, limit: Int): List<BodyWeightDomain> {
        val userId = authProvider.userId ?: throw AuthError.NotAuthenticated
        val weights: List<BodyWeightDTO> = dataProvider.loadLessThanOrEqualAsync(
            from = Constants.Firestore.bodyWeights(userId),
            field = Constants.Firestore.Field.ID,
            value = BodyWeightDayKey.make(upTo),
            limit = limit,
        )
        return weights.map { it.asDomain() }
    }
}
