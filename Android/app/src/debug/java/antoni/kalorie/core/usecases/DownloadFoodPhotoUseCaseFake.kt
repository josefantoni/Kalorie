package antoni.kalorie.core.usecases

class DownloadFoodPhotoUseCaseFake(
    val result: ByteArray = byteArrayOf(1),
    val shouldThrow: Boolean = false,
) : DownloadFoodPhotoUseCaseProtocol {

    // MARK: - Functions

    override suspend fun invoke(url: String): ByteArray {
        if (shouldThrow) throw RuntimeException("unknown")
        return result
    }
}
