package antoni.kalorie.core.networking

import antoni.kalorie.core.models.FoodItemKind
import kotlinx.serialization.SerializationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class FirestoreDataMapperTest {

    // MARK: - decode

    @Test
    fun decode_whenNumbersArriveAsLong_readsThemIntoDoubleFields() {
        val dto = FirestoreDataMapper.decode(makeFoodConsumedData(weight = 100L, date = 1_800_000_000L), FoodConsumedDTO.serializer())

        assertEquals(100.0, dto.weight, 0.0)
        assertEquals(1_800_000_000.0, dto.date, 0.0)
    }

    @Test
    fun decode_whenNumbersArriveAsDouble_keepsTheFraction() {
        val dto = FirestoreDataMapper.decode(makeFoodConsumedData(weight = 12.5, date = 1_800_000_000.5), FoodConsumedDTO.serializer())

        assertEquals(12.5, dto.weight, 0.0)
        assertEquals(1_800_000_000.5, dto.date, 0.0)
    }

    @Test
    fun decode_whenRequiredFieldIsMissing_throws() {
        val data = makeFoodConsumedData(weight = 100L, date = 1L) - "weight"

        try {
            FirestoreDataMapper.decode(data, FoodConsumedDTO.serializer())
            fail("Expected a failed decode, as on iOS")
        } catch (_: SerializationException) {
        }
    }

    @Test
    fun decode_whenOptionalFieldsAreAbsent_defaultsThemToNull() {
        val dto = FirestoreDataMapper.decode(makeFoodConsumedData(weight = 100L, date = 1L), FoodConsumedDTO.serializer())

        assertNull(dto.caloriesPerHundredGrams)
        assertNull(dto.energyKJ)
        assertNull(dto.fatSaturated)
        assertNull(dto.fiber)
        assertNull(dto.mealTypeId)
        assertNull(dto.measureUnit)
    }

    @Test
    fun decode_readsSnakeCaseKeysAndTheKindEnum() {
        val data = makeFoodConsumedData(weight = 100L, date = 1L) + mapOf("food_item_kind" to "created_meal", "cz_name" to "Jogurt")

        val dto = FirestoreDataMapper.decode(data, FoodConsumedDTO.serializer())

        assertEquals(FoodItemKind.CREATED_MEAL, dto.foodItemKind)
        assertEquals("Jogurt", dto.czName)
    }

    @Test
    fun decode_mealTypeUsesCamelCaseKeys() {
        val data = mapOf<String, Any?>("id" to "0", "name" to "Snídaně", "startMinutes" to 300L, "endMinutes" to 480L)

        val dto = FirestoreDataMapper.decode(data, MealTypeDTO.serializer())

        assertEquals(MealTypeDTO(id = "0", name = "Snídaně", startMinutes = 300, endMinutes = 480), dto)
    }

    @Test
    fun decode_ignoresUnknownKeys() {
        val data = makeFoodConsumedData(weight = 100L, date = 1L) + mapOf("written_by_a_newer_client" to true)

        assertEquals("1", FirestoreDataMapper.decode(data, FoodConsumedDTO.serializer()).id)
    }

    // MARK: - encode

    @Test
    fun encode_writesIntegersAsLongAndFractionsAsDouble() {
        val data = FirestoreDataMapper.encode(
            MealTypeDTO(id = "0", name = "Snídaně", startMinutes = 300, endMinutes = 480),
            MealTypeDTO.serializer(),
        )

        assertEquals(300L, data["startMinutes"])
        assertEquals("Snídaně", data["name"])
    }

    @Test
    fun encode_writesWholeDoublesAsDoubleSoTheyStayDoublesInFirestore() {
        val dto = FirestoreDataMapper.decode(makeFoodConsumedData(weight = 100L, date = 1_800_000_000L), FoodConsumedDTO.serializer())

        val data = FirestoreDataMapper.encode(dto, FoodConsumedDTO.serializer())

        assertEquals(100.0, data["weight"])
        assertEquals(1_800_000_000.0, data["date"])
        assertEquals(150L, data["calories"])
    }

    @Test
    fun encode_omitsNilOptionalsInsteadOfWritingNull() {
        val dto = FirestoreDataMapper.decode(makeFoodConsumedData(weight = 100L, date = 1L), FoodConsumedDTO.serializer())

        val data = FirestoreDataMapper.encode(dto, FoodConsumedDTO.serializer())

        assertFalse(data.containsKey("meal_type_id"))
        assertTrue(data.containsKey("food_item_id"))
    }

    // MARK: - Helpers

    private fun makeFoodConsumedData(weight: Number, date: Number): Map<String, Any?> = mapOf(
        "id" to "1",
        "food_item_id" to "1",
        "food_item_kind" to "catalogue",
        "cz_name" to "Vejce",
        "eng_name" to "Egg",
        "weight" to weight,
        "date" to date,
        "calories" to 150L,
        "protein" to 0L,
        "carbohydrate" to 0L,
        "carbohydrate_sugar" to 0L,
        "fat" to 0L,
        "fat_unsaturated" to 0L,
        "salt" to 0L,
    )
}
