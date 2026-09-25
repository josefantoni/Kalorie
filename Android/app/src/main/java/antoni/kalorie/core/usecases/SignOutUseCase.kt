package antoni.kalorie.core.usecases

import antoni.kalorie.core.auth.AuthCommandProviderProtocol
import antoni.kalorie.core.auth.GoogleSessionProviderProtocol
import antoni.kalorie.core.auth.PendingMergeSnapshotStoreProtocol

interface SignOutUseCaseProtocol {
    suspend operator fun invoke()
}

class SignOutUseCase(
    private val authCommandProvider: AuthCommandProviderProtocol,
    private val snapshotStore: PendingMergeSnapshotStoreProtocol,
    private val googleSessionProvider: GoogleSessionProviderProtocol,
) : SignOutUseCaseProtocol {

    // MARK: - Functions

    override suspend fun invoke() {
        snapshotStore.delete()
        authCommandProvider.signOut()
        googleSessionProvider.clearSession()
    }
}
