package antoni.kalorie.core.networking

import antoni.kalorie.core.models.FoodItemDomain
import antoni.kalorie.core.models.FoodItemKind
import antoni.kalorie.core.models.FoodItemSubmissionStatus
import java.time.Instant
import kotlinx.serialization.SerializationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test

class DTOWireShapeTest {

    // MARK: - FavouriteFoodDTO

    @Test
    fun favouriteFood_encodesEveryFieldUnderItsSnakeCaseWireName() {
        val encoded = FirestoreDataMapper.encode(FavouriteFoodDTO(makeItem(), favouritedAt = fixedDate), FavouriteFoodDTO.serializer())

        assertEquals(
            setOf(
                "id", "food_item_kind", "cz_name", "eng_name", "weight", "date", "energy_kj", "calories_per_hundred_grams",
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

    // MARK: - FoodItemSubmissionDTO

    @Test
    fun submission_encodesEveryFieldUnderItsWireNameWithTheItemNested() {
        val dto = FoodItemSubmissionDTO(
            id = "sub-1",
            barcode = "12345678",
            submittedBy = "user-1",
            status = FoodItemSubmissionStatus.REJECTED,
            submittedAt = fixedDate,
            rejectReason = "Wrong calories",
            item = makeItem(),
        )

        val encoded = FirestoreDataMapper.encode(dto, FoodItemSubmissionDTO.serializer())

        assertEquals(setOf("id", "barcode", "submitted_by", "status", "submitted_at", "reject_reason", "item"), encoded.keys)
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
            item = makeItem(),
        )
        val encoded = FirestoreDataMapper.encode(dto, FoodItemSubmissionDTO.serializer()) - "barcode"

        assertNull(FirestoreDataMapper.decode(encoded, FoodItemSubmissionDTO.serializer()).barcode)
    }

    // MARK: - FoodItemReportDTO

    @Test
    fun report_encodesFourFieldsAndStoresTheDateAsEpochSeconds() {
        val encoded = FirestoreDataMapper.encode(
            FoodItemReportDTO(barcode = "12345678", reportedBy = "user-1", reason = "wrong calories", reportedAt = fixedDate),
            FoodItemReportDTO.serializer(),
        )

        assertEquals(setOf("barcode", "reported_by", "reason", "reported_at"), encoded.keys)
        assertEquals(fixedDate.epochSecond.toDouble(), encoded["reported_at"])
    }

    @Test
    fun report_whenReasonIsMissing_failsToDecodeAsOnIOS() {
        val encoded = mapOf<String, Any?>("barcode" to "12345678", "reported_by" to "user-1", "reported_at" to 1.0)

        try {
            FirestoreDataMapper.decode(encoded, FoodItemReportDTO.serializer())
            fail("Expected a failed decode")
        } catch (_: SerializationException) {
        }
    }

    // MARK: - Helpers

    private val fixedDate: Instant = Instant.ofEpochSecond(1_758_800_000)

    private fun makeItem(): FoodItemDomain = FoodItemDomain(
        id = "12345678",
        kind = FoodItemKind.CATALOGUE,
        czName = "Mléko",
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
