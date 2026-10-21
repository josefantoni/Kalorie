package antoni.kalorie.core.usecases

import antoni.kalorie.core.models.AppVersion
import antoni.kalorie.core.networking.AppConfigDTO
import antoni.kalorie.core.networking.FirestoreDataProviderProtocol
import antoni.kalorie.core.networking.loadFromServerAsync
import antoni.kalorie.core.utils.Constants

interface FetchMinimumVersionUseCaseProtocol {
    suspend operator fun invoke(): AppVersion?
}

class FetchMinimumVersionUseCase(
    private val dataProvider: FirestoreDataProviderProtocol,
) : FetchMinimumVersionUseCaseProtocol {

    // MARK: - Functions

    override suspend fun invoke(): AppVersion? {
        val config: AppConfigDTO? = dataProvider.loadFromServerAsync(
            id = Constants.Firestore.APP_CONFIG_DOCUMENT_ID,
            from = Constants.Firestore.CONFIG,
        )
        return config?.minVersionAndroid?.let(AppVersion::parse)
    }
}
