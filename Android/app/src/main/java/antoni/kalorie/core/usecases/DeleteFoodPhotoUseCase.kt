package antoni.kalorie.core.usecases

import antoni.kalorie.core.networking.StorageDataProviderProtocol
import antoni.kalorie.core.utils.Constants
import antoni.kalorie.core.utils.Log
import kotlinx.coroutines.CancellationException

interface DeleteFoodPhotoUseCaseProtocol {
    suspend operator fun invoke(url: String)
}

suspend fun DeleteFoodPhotoUseCaseProtocol.deleteQuietly(url: String) {
    try {
        invoke(url)
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        Log.error(error, Constants.LogCategory.STORAGE)
    }
}

class DeleteFoodPhotoUseCase(
    private val storageProvider: StorageDataProviderProtocol,
) : DeleteFoodPhotoUseCaseProtocol {

    // MARK: - Functions

    override suspend fun invoke(url: String) {
        storageProvider.deleteAsync(url)
    }
}
