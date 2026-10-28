package antoni.kalorie.core.usecases

import antoni.kalorie.core.models.AppSettingsDomain

data class FetchAppSettingsUseCaseFake(
    val stubbedSettings: AppSettingsDomain = AppSettingsDomain.defaults,
    val shouldThrow: Boolean = false,
) : FetchAppSettingsUseCaseProtocol {

    // MARK: - Functions

    override suspend fun invoke(): AppSettingsDomain {
        if (shouldThrow) throw RuntimeException("unknown")
        return stubbedSettings
    }
}

data class SaveAppSettingsUseCaseFake(
    val shouldThrow: Boolean = false,
) : SaveAppSettingsUseCaseProtocol {

    // MARK: - Functions

    override suspend fun invoke(settings: AppSettingsDomain) {
        if (shouldThrow) throw RuntimeException("unknown")
    }
}
