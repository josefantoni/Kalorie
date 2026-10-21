package antoni.kalorie.core.usecases

import antoni.kalorie.core.models.AppVersion

data class FetchMinimumVersionUseCaseFake(
    val stubbedMinimum: AppVersion? = null,
    val shouldThrow: Boolean = false,
) : FetchMinimumVersionUseCaseProtocol {

    // MARK: - Functions

    override suspend fun invoke(): AppVersion? {
        if (shouldThrow) throw RuntimeException("unknown")
        return stubbedMinimum
    }
}
