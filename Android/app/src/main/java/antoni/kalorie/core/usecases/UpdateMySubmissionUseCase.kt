package antoni.kalorie.core.usecases

import antoni.kalorie.core.auth.AuthProviderProtocol
import antoni.kalorie.core.models.FoodItemDomain
import antoni.kalorie.core.models.FoodItemFormPhoto
import antoni.kalorie.core.models.FoodItemSubmissionDomain
import antoni.kalorie.core.networking.FirestoreDataProviderProtocol

interface UpdateMySubmissionUseCaseProtocol {
    suspend operator fun invoke(id: String, item: FoodItemDomain, photo: FoodItemFormPhoto, previousPhotoUrl: String?): FoodItemSubmissionDomain
}

class UpdateMySubmissionUseCase(
    dataProvider: FirestoreDataProviderProtocol,
    authProvider: AuthProviderProtocol,
    uploadFoodPhoto: UploadFoodPhotoUseCaseProtocol,
    deleteFoodPhoto: DeleteFoodPhotoUseCaseProtocol,
) : UpdateMySubmissionUseCaseProtocol {

    // MARK: - Properties

    private val writer = FoodItemSubmissionWriter(dataProvider, authProvider, uploadFoodPhoto, deleteFoodPhoto)

    // MARK: - Functions

    override suspend fun invoke(
        id: String,
        item: FoodItemDomain,
        photo: FoodItemFormPhoto,
        previousPhotoUrl: String?,
    ): FoodItemSubmissionDomain = writer.write(id = id, item = item, photo = photo, previousPhotoUrl = previousPhotoUrl)
}
