package antoni.kalorie.core.usecases

import antoni.kalorie.core.models.MyCreatedMealDomain

data class UpdateMyCreatedMealUseCaseFake(
    val shouldThrow: Boolean = false,
) : UpdateMyCreatedMealUseCaseProtocol {

    // MARK: - Functions

    override suspend fun invoke(meal: MyCreatedMealDomain) {
        if (shouldThrow) throw RuntimeException("unknown")
    }
}
