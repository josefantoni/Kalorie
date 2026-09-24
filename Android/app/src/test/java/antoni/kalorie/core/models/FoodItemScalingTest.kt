package antoni.kalorie.core.models

import antoni.kalorie.FixtureLoader
import java.time.Instant
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FoodItemScalingTest {

    // MARK: - Tests

    @Test
    fun scaled_matchesSharedFixtureCases() {
        val fixture = FixtureLoader.load("food-item-scaling-cases")
        for (scalingCase in fixture.getValue("cases").jsonArray.map { it.jsonObject }) {
            val name = scalingCase.getValue("name").jsonPrimitive.content
            val scaled = makeItem(scalingCase.getValue("item").jsonObject)
                .scaled(toGrams = scalingCase.getValue("grams").jsonPrimitive.double)
            val expected = scalingCase.getValue("expected").jsonObject
            assertEquals(name, expected.getValue("calories").jsonPrimitive.int, scaled.calories)
            assertEquals(name, expected.double("energyKJ"), scaled.energyKJ, ACCURACY)
            assertEquals(name, expected.double("protein"), scaled.protein, ACCURACY)
            assertEquals(name, expected.double("carbohydrate"), scaled.carbohydrate, ACCURACY)
            assertEquals(name, expected.double("carbohydrateSugar"), scaled.carbohydrateSugar, ACCURACY)
            assertEquals(name, expected.double("fat"), scaled.fat, ACCURACY)
            assertEquals(name, expected.double("fatUnsaturated"), scaled.fatUnsaturated, ACCURACY)
            assertEquals(name, expected.double("salt"), scaled.salt, ACCURACY)
            assertOptionalEquals(name, expected.optionalDouble("fatSaturated"), scaled.fatSaturated)
            assertOptionalEquals(name, expected.optionalDouble("fiber"), scaled.fiber)
        }
    }

    // MARK: - Helpers

    private fun assertOptionalEquals(name: String, expected: Double?, actual: Double?) {
        if (expected == null) {
            assertNull(name, actual)
        } else {
            assertEquals(name, expected, actual ?: Double.NaN, ACCURACY)
        }
    }

    private fun JsonObject.double(key: String): Double = getValue(key).jsonPrimitive.double

    private fun JsonObject.optionalDouble(key: String): Double? =
        getValue(key).let { element: JsonElement -> if (element is JsonNull) null else element.jsonPrimitive.double }

    private fun makeItem(fields: JsonObject): FoodItemDomain = FoodItemDomain(
        id = "12345678",
        kind = FoodItemKind.CATALOGUE,
        czName = "Tvaroh",
        engName = "Cottage cheese",
        weight = 200.0,
        date = Instant.now(),
        energyKJ = fields.double("energyKJ"),
        caloriesPerHundredGrams = fields.double("caloriesPerHundredGrams"),
        fat = fields.double("fat"),
        fatSaturated = fields.optionalDouble("fatSaturated"),
        fatUnsaturatedFattyAcids = fields.double("fatUnsaturatedFattyAcids"),
        carbohydrate = fields.double("carbohydrate"),
        carbohydratePureSugar = fields.double("carbohydratePureSugar"),
        fiber = fields.optionalDouble("fiber"),
        protein = fields.double("protein"),
        salt = fields.double("salt"),
    )

    private companion object {
        const val ACCURACY = 1e-9
    }
}
