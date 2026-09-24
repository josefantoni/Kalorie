package antoni.kalorie.core.models

import antoni.kalorie.FixtureLoader
import java.time.Instant
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FoodItemValidationTest {

    // MARK: - Tests

    @Test
    fun validate_withInvalidCode_returnsInvalidCode() {
        assertEquals(FoodItemValidationError.InvalidCode, FoodItemValidation.validate(makeItem(id = "123456789")))
    }

    @Test
    fun validate_withNonASCIIDigitsCode_returnsInvalidCode() {
        assertEquals(FoodItemValidationError.InvalidCode, FoodItemValidation.validate(makeItem(id = "١٢٣٤٥٦٧٨")))
    }

    @Test
    fun validate_withEmptyName_returnsInvalidName() {
        assertEquals(FoodItemValidationError.InvalidName, FoodItemValidation.validate(makeItem(name = "")))
    }

    @Test
    fun validate_withZeroCalories_returnsInvalidCalories() {
        assertEquals(FoodItemValidationError.InvalidCalories, FoodItemValidation.validate(makeItem(caloriesPerHundredGrams = 0.0)))
    }

    @Test
    fun validate_withZeroWeight_returnsInvalidWeight() {
        assertEquals(FoodItemValidationError.InvalidWeight, FoodItemValidation.validate(makeItem(weight = 0.0)))
    }

    @Test
    fun validate_withInvalidPortion_returnsInvalidPortion() {
        val item = makeItem(portions = listOf(FoodPortionDomain(name = "", grams = 30.0)))
        assertEquals(
            FoodItemValidationError.InvalidPortion(FoodPortionError.InvalidName),
            FoodItemValidation.validate(item),
        )
    }

    @Test
    fun validate_withValidItem_returnsNil() {
        assertNull(FoodItemValidation.validate(makeItem()))
    }

    @Test
    fun validate_withUppercaseUUID_returnsNil() {
        assertNull(
            "a barcode-less submission's id is the uppercase UUID of the submission",
            FoodItemValidation.validate(makeItem(id = "9A5E1B2C-8D3F-4A6E-9C1D-7B2A4E5F6C8D")),
        )
    }

    @Test
    fun validate_withLowercaseUUID_returnsInvalidCode() {
        assertEquals(
            "UUID().uuidString is always uppercase; a lowercase id must never be accepted as this item's own identity",
            FoodItemValidationError.InvalidCode,
            FoodItemValidation.validate(makeItem(id = "9a5e1b2c-8d3f-4a6e-9c1d-7b2a4e5f6c8d")),
        )
    }

    @Test
    fun isValidItemId_matchesSharedFixtureCases() {
        val fixture = FixtureLoader.load("food-item-validation-cases")
        for (itemIdCase in fixture.getValue("itemId").jsonArray.map { it.jsonObject }) {
            val input = itemIdCase.getValue("input").jsonPrimitive.content
            val isValid = FoodItemValidation.isValidBarcode(input) || FoodItemValidation.isValidSubmissionUUID(input)
            assertEquals("id \"$input\"", itemIdCase.getValue("valid").jsonPrimitive.boolean, isValid)
        }
    }

    @Test
    fun validate_matchesSharedFixtureCases() {
        val fixture = FixtureLoader.load("food-item-validation-cases")
        val base = fixture.getValue("foodItemBase").jsonObject
        for (foodItemCase in fixture.getValue("foodItem").jsonArray.map { it.jsonObject }) {
            val overrides = foodItemCase.getValue("overrides").jsonObject
            fun field(key: String) = overrides[key] ?: base.getValue(key)
            val item = makeItem(
                id = field("id").jsonPrimitive.content,
                name = field("czName").jsonPrimitive.content,
                weight = field("weight").jsonPrimitive.double,
                caloriesPerHundredGrams = field("caloriesPerHundredGrams").jsonPrimitive.double,
                portions = field("portions").jsonArray.toPortions(),
            )
            val expected = foodItemCase.getValue("expected").let { if (it is JsonNull) null else it.jsonPrimitive.content }
            assertEquals(
                foodItemCase.getValue("name").jsonPrimitive.content,
                expected,
                FoodItemValidation.validate(item)?.let(::fixtureName),
            )
        }
    }

    // MARK: - Helpers

    private fun JsonArray.toPortions(): List<FoodPortionDomain> = map {
        val portion: JsonObject = it.jsonObject
        FoodPortionDomain(
            name = portion.getValue("name").jsonPrimitive.content,
            grams = portion.getValue("grams").jsonPrimitive.double,
        )
    }

    private fun fixtureName(error: FoodItemValidationError): String = when (error) {
        FoodItemValidationError.InvalidCode -> "invalidCode"
        FoodItemValidationError.InvalidName -> "invalidName"
        FoodItemValidationError.InvalidCalories -> "invalidCalories"
        FoodItemValidationError.InvalidWeight -> "invalidWeight"
        is FoodItemValidationError.InvalidPortion -> when (error.error) {
            FoodPortionError.InvalidName -> "invalidPortion.invalidName"
            FoodPortionError.InvalidGrams -> "invalidPortion.invalidGrams"
            FoodPortionError.TooMany -> "invalidPortion.tooMany"
        }
    }

    private fun makeItem(
        id: String = "12345678",
        name: String = "Tvaroh",
        weight: Double = 200.0,
        caloriesPerHundredGrams: Double = 80.0,
        portions: List<FoodPortionDomain> = emptyList(),
    ): FoodItemDomain = FoodItemDomain(
        id = id,
        kind = FoodItemKind.CATALOGUE,
        czName = name,
        engName = "Cottage cheese",
        weight = weight,
        date = Instant.now(),
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
        portions = portions,
    )
}
