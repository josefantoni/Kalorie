package antoni.kalorie.core.usecases

import antoni.kalorie.core.auth.AuthError
import antoni.kalorie.core.auth.AuthProviderProtocol
import antoni.kalorie.core.models.FoodItemDomain
import antoni.kalorie.core.models.FoodItemSubmissionDomain
import antoni.kalorie.core.models.FoodItemSubmissionError
import antoni.kalorie.core.models.FoodItemSubmissionStatus
import antoni.kalorie.core.models.FoodItemValidation
import antoni.kalorie.core.networking.FirestoreDataProviderProtocol
import antoni.kalorie.core.networking.FoodItemDTO
import antoni.kalorie.core.networking.FoodItemSubmissionDTO
import antoni.kalorie.core.networking.loadFromServerAsync
import antoni.kalorie.core.networking.setAsync
import antoni.kalorie.core.utils.Constants
import java.time.Instant

object FoodItemSubmissionWriter {

    // MARK: - Functions

    suspend fun write(
        id: String,
        item: FoodItemDomain,
        dataProvider: FirestoreDataProviderProtocol,
        authProvider: AuthProviderProtocol,
    ): FoodItemSubmissionDomain {
        val userId = authProvider.userId ?: throw AuthError.NotAuthenticated
        val resolvedItem = if (item.id.isEmpty()) item.withId(id) else item
        FoodItemValidation.validate(resolvedItem)?.let { throw FoodItemSubmissionError.from(it) }
        val existing: FoodItemDTO? = dataProvider.loadFromServerAsync(id = resolvedItem.id, from = Constants.Firestore.FOOD_ITEMS)
        if (existing != null) throw FoodItemSubmissionError.ItemAlreadyExists
        val submission = FoodItemSubmissionDomain(
            id = id,
            barcode = resolvedItem.barcode,
            submittedBy = userId,
            status = FoodItemSubmissionStatus.PENDING,
            submittedAt = Instant.now(),
            rejectReason = null,
            item = resolvedItem,
        )
        val dto = FoodItemSubmissionDTO(
            id = submission.id,
            barcode = submission.barcode,
            submittedBy = submission.submittedBy,
            status = submission.status,
            submittedAt = submission.submittedAt,
            rejectReason = submission.rejectReason,
            item = submission.item,
        )
        dataProvider.setAsync(dto, id = submission.id, inCollection = Constants.Firestore.FOOD_ITEM_SUBMISSIONS)
        return submission
    }
}
