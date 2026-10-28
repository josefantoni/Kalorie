package antoni.kalorie.core.usecases

import antoni.kalorie.core.models.BodyWeightDomain
import java.time.Instant

data class FetchBodyWeightsUseCaseFake(
    val stubbedWeights: List<BodyWeightDomain> = emptyList(),
    val stubbedLaterWeights: List<BodyWeightDomain> = emptyList(),
    val shouldThrow: Boolean = false,
) : FetchBodyWeightsUseCaseProtocol {

    // MARK: - Functions

    override suspend fun upTo(day: Instant): List<BodyWeightDomain> {
        if (shouldThrow) throw RuntimeException("unknown")
        return stubbedWeights
    }

    override suspend fun after(day: Instant): List<BodyWeightDomain> {
        if (shouldThrow) throw RuntimeException("unknown")
        return stubbedLaterWeights
    }

    override suspend fun between(from: Instant, to: Instant): List<BodyWeightDomain> {
        if (shouldThrow) throw RuntimeException("unknown")
        return stubbedWeights
    }

    override suspend fun inYear(year: Int): List<BodyWeightDomain> {
        if (shouldThrow) throw RuntimeException("unknown")
        return stubbedWeights.filter { it.dayKey.startsWith("%04d-".format(year)) }
    }
}
