package antoni.kalorie.core.usecases

import antoni.kalorie.core.auth.AuthProviderProtocol
import antoni.kalorie.core.models.FoodItemDomain
import antoni.kalorie.core.models.FoodItemFormPhoto
import antoni.kalorie.core.models.FoodItemSubmissionDomain
import antoni.kalorie.core.networking.FirestoreDataProviderProtocol
import java.util.UUID

interface SubmitFoodItemUseCaseProtocol {
    suspend operator fun invoke(item: FoodItemDomain, photo: FoodItemFormPhoto): FoodItemSubmissionDomain
}

class SubmitFoodItemUseCase(
    dataProvider: FirestoreDataProviderProtocol,
    authProvider: AuthProviderProtocol,
    uploadFoodPhoto: UploadFoodPhotoUseCaseProtocol,
    deleteFoodPhoto: DeleteFoodPhotoUseCaseProtocol,
) : SubmitFoodItemUseCaseProtocol {

    // MARK: - Properties

    private val writer = FoodItemSubmissionWriter(dataProvider, authProvider, uploadFoodPhoto, deleteFoodPhoto)

    // MARK: - Functions

    override suspend fun invoke(item: FoodItemDomain, photo: FoodItemFormPhoto): FoodItemSubmissionDomain = writer.write(
        id = UUID.randomUUID().toString().uppercase(),
        item = item,
        photo = photo,
        previousPhotoUrl = null,
    )
}
