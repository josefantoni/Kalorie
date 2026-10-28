package antoni.kalorie.core.usecases

import java.time.Instant

data class SaveBodyWeightUseCaseFake(
    val shouldThrow: Boolean = false,
) : SaveBodyWeightUseCaseProtocol {

    // MARK: - Functions

    override suspend fun invoke(kilograms: Double, day: Instant) {
        if (shouldThrow) throw RuntimeException("unknown")
    }
}
