package antoni.kalorie.core.usecases

import antoni.kalorie.core.networking.StorageDataProviderProtocol
import antoni.kalorie.core.utils.Constants
import java.util.UUID

interface UploadFoodPhotoUseCaseProtocol {
    suspend operator fun invoke(data: ByteArray, folder: String): String
}

class UploadFoodPhotoUseCase(
    private val storageProvider: StorageDataProviderProtocol,
) : UploadFoodPhotoUseCaseProtocol {

    // MARK: - Functions

    override suspend fun invoke(data: ByteArray, folder: String): String = storageProvider.uploadAsync(
        data = data,
        path = "$folder/${UUID.randomUUID().toString().uppercase()}.jpg",
        contentType = Constants.Storage.PHOTO_CONTENT_TYPE,
    )
}
