package antoni.kalorie.core.usecases

import antoni.kalorie.core.auth.AuthError
import antoni.kalorie.core.auth.AuthProviderProtocol
import antoni.kalorie.core.models.TicketStatus
import antoni.kalorie.core.models.UserNotificationDomain
import antoni.kalorie.core.networking.FirestoreDataProviderProtocol
import antoni.kalorie.core.networking.FoodItemReportDTO
import antoni.kalorie.core.networking.PhotoSuggestionDTO
import antoni.kalorie.core.networking.loadAsync
import antoni.kalorie.core.utils.Constants
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

interface FetchUserNotificationsUseCaseProtocol {
    suspend operator fun invoke(): List<UserNotificationDomain>
}

class FetchUserNotificationsUseCase(
    private val dataProvider: FirestoreDataProviderProtocol,
    private val authProvider: AuthProviderProtocol,
) : FetchUserNotificationsUseCaseProtocol {

    // MARK: - Functions

    override suspend fun invoke(): List<UserNotificationDomain> {
        val userId = authProvider.userId ?: throw AuthError.NotAuthenticated
        val (reports, suggestions) = coroutineScope {
            val reports = async {
                dataProvider.loadAsync<FoodItemReportDTO>(
                    from = Constants.Firestore.FOOD_ITEM_REPORTS,
                    whereEqualTo = listOf(Constants.Firestore.Field.REPORTED_BY to userId),
                    isHidden = false,
                    orderBy = Constants.Firestore.Field.REPORTED_AT,
                    descending = true,
                )
            }
            val suggestions = async {
                dataProvider.loadAsync<PhotoSuggestionDTO>(
                    from = Constants.Firestore.PHOTO_SUGGESTIONS,
                    whereEqualTo = listOf(Constants.Firestore.Field.SUGGESTED_BY to userId),
                    isHidden = false,
                    orderBy = Constants.Firestore.Field.SUGGESTED_AT,
                    descending = true,
                )
            }
            reports.await() to suggestions.await()
        }
        val reportNotifications = reports.map { it.asDomain() }.map {
            UserNotificationDomain(
                kind = UserNotificationDomain.Kind.REPORT,
                id = it.id,
                itemId = it.barcode,
                createdAt = it.reportedAt,
                status = it.status,
                response = it.response,
                resolvedAt = it.resolvedAt,
                seenAt = it.seenAt,
                reason = it.reason,
            )
        }
        val suggestionNotifications = suggestions.map { it.asDomain() }.map {
            UserNotificationDomain(
                kind = UserNotificationDomain.Kind.PHOTO_SUGGESTION,
                id = it.id,
                itemId = it.itemId,
                createdAt = it.suggestedAt,
                status = it.status,
                response = it.response,
                resolvedAt = it.resolvedAt,
                seenAt = it.seenAt,
                photoUrl = it.photoUrl.takeIf { _ -> it.status == TicketStatus.PENDING },
            )
        }
        return (reportNotifications + suggestionNotifications).sortedByDescending { it.lastEventAt }
    }
}
