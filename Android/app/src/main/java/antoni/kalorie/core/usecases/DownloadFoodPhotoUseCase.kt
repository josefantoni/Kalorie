package antoni.kalorie.core.usecases

import antoni.kalorie.core.networking.StorageDataProviderProtocol

interface DownloadFoodPhotoUseCaseProtocol {
    suspend operator fun invoke(url: String): ByteArray
}

class DownloadFoodPhotoUseCase(
    private val storageProvider: StorageDataProviderProtocol,
) : DownloadFoodPhotoUseCaseProtocol {

    // MARK: - Functions

    override suspend fun invoke(url: String): ByteArray = storageProvider.downloadAsync(url)
}
