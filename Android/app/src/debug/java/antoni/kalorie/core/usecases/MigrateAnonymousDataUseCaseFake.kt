package antoni.kalorie.core.usecases

import com.google.firebase.auth.AuthCredential

class MigrateAnonymousDataUseCaseFake : MigrateAnonymousDataUseCaseProtocol {

    // MARK: - Properties

    var migrateError: Exception? = null
    var resumeError: Exception? = null
    var migrateCallCount = 0
        private set
    var lastSourceUserId: String? = null
        private set
    var lastCredential: AuthCredential? = null
        private set
    var resumeCallCount = 0
        private set

    // MARK: - Functions

    override suspend fun migrate(sourceUserId: String, credential: AuthCredential) {
        migrateCallCount += 1
        lastSourceUserId = sourceUserId
        lastCredential = credential
        migrateError?.let { throw it }
    }

    override suspend fun resumeIfNeeded() {
        resumeCallCount += 1
        resumeError?.let { throw it }
    }
}
