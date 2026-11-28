package antoni.kalorie.core.networking

import antoni.kalorie.core.models.FoodItemDomain
import antoni.kalorie.core.models.FoodItemKind
import antoni.kalorie.core.models.FoodItemSubmissionStatus
import antoni.kalorie.core.models.TicketStatus
import kotlinx.serialization.SerializationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.time.Instant

class DTOWireShapeTest {

    // MARK: - Properties

    private val fixedDate: Instant = Instant.ofEpochSecond(1_758_800_000)

    @Test
    fun favouriteFood_encodesEveryFieldUnderItsSnakeCaseWireName() {
        val encoded = FirestoreDataMapper.encode(FavouriteFoodDTO(makeItem(), favouritedAt = fixedDate), FavouriteFoodDTO.serializer())

        assertEquals(
            setOf(
                "id", "food_item_kind", "cz_name", "sk_name", "eng_name", "weight", "date", "energy_kj", "calories_per_hundred_grams",
                "fat", "fat_saturated", "fat_unsaturated_fatty_acids", "carbohydrate", "carbohydrate_pure_sugar", "fiber",
                "protein", "salt", "favourited_at", "portions", "measure_unit",
            ),
            encoded.keys,
        )
        assertEquals("catalogue", encoded["food_item_kind"])
        assertEquals(fixedDate.epochSecond.toDouble(), encoded["favourited_at"])
    }

    @Test
    fun favouriteFood_whenFoodItemKindIsMissing_failsToDecodeAsOnIOS() {
        val encoded = FirestoreDataMapper.encode(FavouriteFoodDTO(makeItem(), favouritedAt = fixedDate), FavouriteFoodDTO.serializer()) - "food_item_kind"

        try {
            FirestoreDataMapper.decode(encoded, FavouriteFoodDTO.serializer())
            fail("Expected a failed decode")
        } catch (_: SerializationException) {
        }
    }

    @Test
    fun submission_encodesEveryFieldUnderItsWireNameWithTheItemNested() {
        val dto = FoodItemSubmissionDTO(
            id = "sub-1",
            barcode = "12345678",
            submittedBy = "user-1",
            status = FoodItemSubmissionStatus.REJECTED,
            submittedAt = fixedDate,
            rejectReason = "Wrong calories",
            nameLanguage = "cs",
            item = makeItem(),
        )

        val encoded = FirestoreDataMapper.encode(dto, FoodItemSubmissionDTO.serializer())

        assertEquals(setOf("id", "barcode", "submitted_by", "status", "submitted_at", "reject_reason", "name_language", "item"), encoded.keys)
        assertEquals("rejected", encoded["status"])
        assertEquals(fixedDate.epochSecond.toDouble(), encoded["submitted_at"])
        assertEquals("12345678", (encoded["item"] as Map<*, *>)["id"])
    }

    @Test
    fun submission_whenBarcodeIsAbsent_decodesAsAFoodWithoutABarcode() {
        val dto = FoodItemSubmissionDTO(
            id = "sub-1",
            barcode = null,
            submittedBy = "user-1",
            status = FoodItemSubmissionStatus.PENDING,
            submittedAt = fixedDate,
            rejectReason = null,
            nameLanguage = "cs",
            item = makeItem(),
        )
        val encoded = FirestoreDataMapper.encode(dto, FoodItemSubmissionDTO.serializer()) - "barcode"

        assertNull(FirestoreDataMapper.decode(encoded, FoodItemSubmissionDTO.serializer()).barcode)
    }

    @Test
    fun reportCreate_encodesTheKeysTheCreateRuleAllowsAndStoresTheDateAsEpochSeconds() {
        val encoded = FirestoreDataMapper.encode(
            FoodItemReportCreateDTO(barcode = "12345678", reportedBy = "user-1", reason = "wrong calories", reportedAt = fixedDate, locale = "cs"),
            FoodItemReportCreateDTO.serializer(),
        )

        assertEquals(setOf("barcode", "reported_by", "reason", "reported_at", "status", "locale", "hidden"), encoded.keys)
        assertEquals(fixedDate.epochSecond.toDouble(), encoded["reported_at"])
        assertEquals("pending", encoded["status"])
        assertEquals(false, encoded["hidden"])
        assertEquals("cs", encoded["locale"])
    }

    @Test
    fun reportCreate_carriesNoMaintainerOrReadStateKeys() {
        val encoded = FirestoreDataMapper.encode(
            FoodItemReportCreateDTO(barcode = "12345678", reportedBy = "alice", reason = "wrong fat", reportedAt = fixedDate, locale = "en"),
            FoodItemReportCreateDTO.serializer(),
        )

        assertTrue("the create rule lists the allowed keys, so a response, resolved_at or seen_at would be refused", encoded.keys.intersect(setOf("response", "resolved_at", "seen_at", "author_deleted")).isEmpty())
    }

    @Test
    fun photoSuggestionCreate_encodesTheKeysTheCreateRuleAllowsAndNoMaintainerOrReadStateKeys() {
        val encoded = FirestoreDataMapper.encode(
            PhotoSuggestionCreateDTO(itemId = "12345678", suggestedBy = "alice", photoUrl = "https://example.com/a.jpg", suggestedAt = fixedDate, locale = "sk"),
            PhotoSuggestionCreateDTO.serializer(),
        )

        assertEquals(setOf("item_id", "suggested_by", "photo_url", "suggested_at", "status", "locale", "hidden"), encoded.keys)
        assertEquals("pending", encoded["status"])
        assertEquals(false, encoded["hidden"])
        assertEquals("sk", encoded["locale"])
    }

    @Test
    fun ticketResolution_encodesExactlyTheKeysTheResolveRuleAllows() {
        val encoded = FirestoreDataMapper.encode(
            TicketResolutionDTO(status = TicketStatus.REJECTED, response = "Not reproducible", resolvedAt = fixedDate),
            TicketResolutionDTO.serializer(),
        )

        assertEquals(setOf("status", "response", "resolved_at", "hidden"), encoded.keys)
        assertEquals("rejected", encoded["status"])
        assertEquals(false, encoded["hidden"])
    }

    @Test
    fun notificationSeen_encodesOnlySeenAt_andHidden_encodesOnlyHiddenTrue() {
        val seen = FirestoreDataMapper.encode(NotificationSeenDTO(seenAt = fixedDate), NotificationSeenDTO.serializer())
        val hidden = FirestoreDataMapper.encode(NotificationHiddenDTO(), NotificationHiddenDTO.serializer())

        assertEquals(setOf("seen_at"), seen.keys)
        assertEquals(mapOf<String, Any?>("hidden" to true), hidden)
    }

    @Test
    fun report_decodesAnOwnerlessResolvedTicketWithItsDocumentId() {
        val encoded = mapOf<String, Any?>(
            "id" to "ticket-1",
            "barcode" to "12345678",
            "reason" to "wrong fat",
            "reported_at" to 1.0,
            "status" to "accepted",
            "response" to "Fixed",
            "resolved_at" to 2.0,
            "author_deleted" to true,
        )

        val dto = FirestoreDataMapper.decode(encoded, FoodItemReportDTO.serializer())

        assertEquals("ticket-1", dto.id)
        assertNull(dto.reportedBy)
        assertEquals(TicketStatus.ACCEPTED, dto.status)
        assertEquals("en", dto.asDomain().locale)
    }

    @Test
    fun report_whenReasonIsMissing_failsToDecodeAsOnIOS() {
        val encoded = mapOf<String, Any?>("id" to "ticket-1", "barcode" to "12345678", "reported_by" to "user-1", "reported_at" to 1.0, "status" to "pending")

        try {
            FirestoreDataMapper.decode(encoded, FoodItemReportDTO.serializer())
            fail("Expected a failed decode")
        } catch (_: SerializationException) {
        }
    }

    // MARK: - Helpers

    private fun makeItem(): FoodItemDomain = FoodItemDomain(
        id = "12345678",
        kind = FoodItemKind.CATALOGUE,
        czName = "Mléko",
        skName = "Mléko",
        engName = "Milk",
        weight = 1000.0,
        date = fixedDate,
        energyKJ = 270.0,
        caloriesPerHundredGrams = 64.0,
        fat = 3.5,
        fatSaturated = 2.1,
        fatUnsaturatedFattyAcids = 1.0,
        carbohydrate = 4.8,
        carbohydratePureSugar = 4.8,
        fiber = 0.0,
        protein = 3.2,
        salt = 0.1,
    )
}
