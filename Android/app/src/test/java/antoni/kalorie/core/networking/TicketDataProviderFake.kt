package antoni.kalorie.core.networking

import antoni.kalorie.core.models.FoodItemDomain
import antoni.kalorie.core.models.FoodItemKind
import antoni.kalorie.core.models.FoodItemReportDomain
import antoni.kalorie.core.models.PhotoSuggestionDomain
import antoni.kalorie.core.models.TicketStatus
import antoni.kalorie.core.models.UserNotificationDomain
import antoni.kalorie.core.utils.Constants
import kotlinx.serialization.KSerializer
import java.time.Instant

@Suppress("UNCHECKED_CAST")
class TicketDataProviderFake(
    private val base: FirestoreDataProviderProtocol = FirestoreDataProviderFake(),
) : FirestoreDataProviderProtocol by base {

    // MARK: - Types

    data class Write(val id: String, val collection: String, val item: Any?)

    data class OrderedQuery(
        val collection: String,
        val filters: List<Pair<String, String>>,
        val isHidden: Boolean?,
        val orderField: String,
        val descending: Boolean,
    )

    data class EqualityQuery(val collection: String, val filters: List<Pair<String, String>>, val limit: Int)

    // MARK: - Properties

    var currentItem: FoodItemDomain? = null
    var stubbedReports: List<FoodItemReportDTO> = emptyList()
    var stubbedSuggestions: List<PhotoSuggestionDTO> = emptyList()
    var stubbedDocumentIds: Map<String, List<String>> = emptyMap()
    var stubbedCounts: Map<String, Int> = emptyMap()
    var failingWriteCollections: Set<String> = emptySet()
    var failingLoadCollections: Set<String> = emptySet()
    var failingError: Exception = java.io.IOException("offline")
    var failingAnonymisation = false
    var newIds: MutableList<String> = mutableListOf("ticket-1", "ticket-2", "ticket-3")
    val sets: MutableList<Write> = java.util.Collections.synchronizedList(mutableListOf())
    val updates: MutableList<Write> = java.util.Collections.synchronizedList(mutableListOf())
    val deletes: MutableList<Pair<String, String>> = java.util.Collections.synchronizedList(mutableListOf())
    val anonymised: MutableList<Triple<String, String, String>> = java.util.Collections.synchronizedList(mutableListOf())
    val orderedQueries: MutableList<OrderedQuery> = java.util.Collections.synchronizedList(mutableListOf())
    val equalityQueries: MutableList<EqualityQuery> = java.util.Collections.synchronizedList(mutableListOf())
    val events: MutableList<String> = java.util.Collections.synchronizedList(mutableListOf())

    // MARK: - Functions

    override suspend fun <T> loadAsync(
        from: String,
        field: String,
        isEqualTo: String,
        orderBy: String,
        descending: Boolean,
        serializer: KSerializer<T>,
    ): List<T> {
        orderedQueries.add(OrderedQuery(from, listOf(field to isEqualTo), null, orderBy, descending))
        return stubbed(from)
    }

    override suspend fun <T> loadAsync(
        from: String,
        whereEqualTo: List<Pair<String, String>>,
        isHidden: Boolean,
        orderBy: String,
        descending: Boolean,
        serializer: KSerializer<T>,
    ): List<T> {
        orderedQueries.add(OrderedQuery(from, whereEqualTo, isHidden, orderBy, descending))
        return stubbed(from)
    }

    override suspend fun <T> loadAsync(from: String, whereEqualTo: List<Pair<String, String>>, limit: Int, serializer: KSerializer<T>): List<T> {
        equalityQueries.add(EqualityQuery(from, whereEqualTo, limit))
        return stubbed(from)
    }

    override suspend fun <T> loadAsync(id: String, from: String, serializer: KSerializer<T>): T? {
        val item = currentItem
        if (from != Constants.Firestore.FOOD_ITEMS || item == null) return null
        return FoodItemDTO(item) as T
    }

    override fun newDocumentId(inCollection: String): String = if (newIds.isEmpty()) java.util.UUID.randomUUID().toString() else newIds.removeAt(0)

    override suspend fun <T> setAsync(item: T, id: String, inCollection: String, serializer: KSerializer<T>) {
        if (inCollection in failingWriteCollections) throw failingError
        sets.add(Write(id, inCollection, item))
        events.add("set $inCollection/$id")
    }

    override suspend fun <T> updateAsync(item: T, id: String, inCollection: String, serializer: KSerializer<T>) {
        if (inCollection in failingWriteCollections) throw failingError
        updates.add(Write(id, inCollection, item))
        events.add("update $inCollection/$id")
    }

    override suspend fun deleteAsync(id: String, from: String) {
        if (from in failingWriteCollections) throw failingError
        deletes.add(id to from)
        events.add("delete $from/$id")
    }

    override suspend fun anonymiseAsync(id: String, ownerField: String, inCollection: String) {
        if (failingAnonymisation) throw failingError
        anonymised.add(Triple(id, ownerField, inCollection))
    }

    override suspend fun loadDocumentIdsAsync(from: String, field: String, isEqualTo: String): List<String> = stubbedDocumentIds[from].orEmpty()

    override suspend fun countAsync(from: String, field: String, isEqualTo: String): Int = stubbedCounts[from] ?: 0

    // MARK: - Private

    private fun <T> stubbed(collection: String): List<T> {
        if (collection in failingLoadCollections) throw failingError
        return when (collection) {
            Constants.Firestore.FOOD_ITEM_REPORTS -> stubbedReports as List<T>
            Constants.Firestore.PHOTO_SUGGESTIONS -> stubbedSuggestions as List<T>
            else -> emptyList()
        }
    }
}

val ticketCreatedAt: Instant = Instant.ofEpochSecond(1_700_000_000)

fun makeReportDTO(
    id: String = "report-1",
    barcode: String = "12345678",
    reportedBy: String? = "user-1",
    reportedAt: Instant = ticketCreatedAt,
    status: TicketStatus = TicketStatus.PENDING,
    locale: String? = "en",
    response: String? = null,
    resolvedAt: Instant? = null,
    seenAt: Instant? = null,
): FoodItemReportDTO = FoodItemReportDTO(
    id = id,
    barcode = barcode,
    reportedBy = reportedBy,
    reason = "wrong fat",
    reportedAt = reportedAt.epochSecond.toDouble(),
    status = status,
    locale = locale,
    response = response,
    resolvedAt = resolvedAt?.epochSecond?.toDouble(),
    seenAt = seenAt?.epochSecond?.toDouble(),
)

fun makeSuggestionDTO(
    id: String = "photo-1",
    itemId: String = "12345678",
    suggestedBy: String? = "user-1",
    suggestedAt: Instant = ticketCreatedAt,
    status: TicketStatus = TicketStatus.PENDING,
    locale: String? = "en",
    response: String? = null,
    resolvedAt: Instant? = null,
    seenAt: Instant? = null,
): PhotoSuggestionDTO = PhotoSuggestionDTO(
    id = id,
    itemId = itemId,
    suggestedBy = suggestedBy,
    photoUrl = "https://example.com/$itemId/$id.jpg",
    suggestedAt = suggestedAt.epochSecond.toDouble(),
    status = status,
    locale = locale,
    response = response,
    resolvedAt = resolvedAt?.epochSecond?.toDouble(),
    seenAt = seenAt?.epochSecond?.toDouble(),
)

fun makeReport(id: String = "report-1", barcode: String = "12345678", locale: String = "en"): FoodItemReportDomain = makeReportDTO(id = id, barcode = barcode, locale = locale).asDomain()

fun makeSuggestion(id: String = "photo-1", itemId: String = "12345678", locale: String = "en"): PhotoSuggestionDomain = makeSuggestionDTO(id = id, itemId = itemId, locale = locale).asDomain()

fun makeNotification(
    kind: UserNotificationDomain.Kind = UserNotificationDomain.Kind.REPORT,
    id: String = "ticket-1",
    itemId: String = "12345678",
    createdAt: Instant = ticketCreatedAt,
    status: TicketStatus = TicketStatus.PENDING,
    resolvedAt: Instant? = null,
    seenAt: Instant? = null,
): UserNotificationDomain = UserNotificationDomain(
    kind = kind,
    id = id,
    itemId = itemId,
    createdAt = createdAt,
    status = status,
    resolvedAt = resolvedAt,
    seenAt = seenAt,
    photoUrl = if (kind == UserNotificationDomain.Kind.PHOTO_SUGGESTION) "https://example.com/$itemId/$id.jpg" else null,
)

fun makePhotoSuggestionItem(id: String = "12345678", photoUrl: String? = null, caloriesPerHundredGrams: Double = 80.0): FoodItemDomain = FoodItemDomain(
    id = id,
    kind = FoodItemKind.CATALOGUE,
    czName = "Tvaroh",
    engName = "",
    weight = 200.0,
    date = Instant.ofEpochSecond(1_700_000_000),
    energyKJ = 335.0,
    caloriesPerHundredGrams = caloriesPerHundredGrams,
    fat = 0.5,
    fatSaturated = 0.3,
    fatUnsaturatedFattyAcids = 0.2,
    carbohydrate = 4.0,
    carbohydratePureSugar = 3.0,
    fiber = 0.0,
    protein = 13.0,
    salt = 0.1,
    photoUrl = photoUrl,
)
