package antoni.kalorie.core.usecases

data class UploadFoodPhotoUseCaseFake(
    val result: String = "https://storage.fake/photo.jpg",
    val shouldThrow: Boolean = false,
) : UploadFoodPhotoUseCaseProtocol {

    // MARK: - Functions

    override suspend fun invoke(data: ByteArray, folder: String): String {
        if (shouldThrow) throw RuntimeException("unknown")
        return result
    }
}
