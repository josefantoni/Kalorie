package antoni.kalorie.core.usecases

import antoni.kalorie.core.auth.AuthError
import antoni.kalorie.core.auth.AuthProviderProtocol
import antoni.kalorie.core.models.BodyWeightDayKey
import antoni.kalorie.core.models.BodyWeightDomain
import antoni.kalorie.core.models.BodyWeightUnit
import antoni.kalorie.core.networking.BodyWeightDTO
import antoni.kalorie.core.networking.FirestoreDataProviderProtocol
import antoni.kalorie.core.networking.setAsync
import antoni.kalorie.core.utils.Constants
import java.time.Instant

sealed class BodyWeightError : Exception() {
    data object OutOfRange : BodyWeightError()
    data object FutureDay : BodyWeightError()
}

interface SaveBodyWeightUseCaseProtocol {
    suspend operator fun invoke(kilograms: Double, day: Instant)
}

class SaveBodyWeightUseCase(
    private val dataProvider: FirestoreDataProviderProtocol,
    private val authProvider: AuthProviderProtocol,
    private val now: () -> Instant = Instant::now,
) : SaveBodyWeightUseCaseProtocol {

    // MARK: - Functions

    override suspend fun invoke(kilograms: Double, day: Instant) {
        val userId = authProvider.userId ?: throw AuthError.NotAuthenticated
        if (kilograms !in BodyWeightUnit.MIN_KILOGRAMS..BodyWeightUnit.MAX_KILOGRAMS) throw BodyWeightError.OutOfRange
        val dayKey = BodyWeightDayKey.make(day)
        if (dayKey > BodyWeightDayKey.make(now())) throw BodyWeightError.FutureDay
        dataProvider.setAsync(
            BodyWeightDTO(BodyWeightDomain(dayKey = dayKey, kilograms = kilograms)),
            id = dayKey,
            inCollection = Constants.Firestore.bodyWeights(userId),
        )
    }
}
