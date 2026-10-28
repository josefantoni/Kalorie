package antoni.kalorie.core.usecases

import antoni.kalorie.core.auth.AuthError
import antoni.kalorie.core.auth.AuthProviderProtocol
import antoni.kalorie.core.models.AppSettingsDomain
import antoni.kalorie.core.networking.AppSettingsDTO
import antoni.kalorie.core.networking.FirestoreDataProviderProtocol
import antoni.kalorie.core.networking.loadAsync
import antoni.kalorie.core.networking.mergeAsync
import antoni.kalorie.core.utils.Constants

interface FetchAppSettingsUseCaseProtocol {
    suspend operator fun invoke(): AppSettingsDomain
}

class FetchAppSettingsUseCase(
    private val dataProvider: FirestoreDataProviderProtocol,
    private val authProvider: AuthProviderProtocol,
) : FetchAppSettingsUseCaseProtocol {

    // MARK: - Functions

    override suspend fun invoke(): AppSettingsDomain {
        val userId = authProvider.userId ?: throw AuthError.NotAuthenticated
        val dto: AppSettingsDTO? = dataProvider.loadAsync(
            id = Constants.Firestore.APP_SETTINGS_DOCUMENT_ID,
            from = Constants.Firestore.settings(userId),
        )
        return dto?.asDomain() ?: AppSettingsDomain.defaults
    }
}

interface SaveAppSettingsUseCaseProtocol {
    suspend operator fun invoke(settings: AppSettingsDomain)
}

class SaveAppSettingsUseCase(
    private val dataProvider: FirestoreDataProviderProtocol,
    private val authProvider: AuthProviderProtocol,
) : SaveAppSettingsUseCaseProtocol {

    // MARK: - Functions

    override suspend fun invoke(settings: AppSettingsDomain) {
        val userId = authProvider.userId ?: throw AuthError.NotAuthenticated
        dataProvider.mergeAsync(
            AppSettingsDTO(settings),
            id = Constants.Firestore.APP_SETTINGS_DOCUMENT_ID,
            inCollection = Constants.Firestore.settings(userId),
        )
    }
}
