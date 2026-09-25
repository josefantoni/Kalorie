package antoni.kalorie.core.usecases

data class FetchMaintainerClaimUseCaseFake(
    val stubbedIsMaintainer: Boolean = false,
) : FetchMaintainerClaimUseCaseProtocol {

    // MARK: - Functions

    override suspend fun invoke(): Boolean = stubbedIsMaintainer
}
