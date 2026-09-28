package antoni.kalorie.core.nutritionlabelrecognition

import antoni.kalorie.FixtureLoader
import antoni.kalorie.core.models.FoodMeasure
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NutritionLabelParserTest {

    // MARK: - Tests

    @Test
    fun parse_matchesSharedFixtureCases() {
        val fixture = FixtureLoader.load("nutrition-label-parsing-cases")
        for (parsingCase in fixture.getValue("cases").jsonArray.map { it.jsonObject }) {
            val name = parsingCase.getValue("name").jsonPrimitive.content
            val lines = parsingCase.getValue("lines").jsonArray.map { it.jsonObject }.map { line ->
                RecognizedTextLine(
                    text = line.getValue("text").jsonPrimitive.content,
                    boundingBox = NormalizedRect(line.double("x"), line.double("y"), line.double("width"), line.double("height")),
                )
            }
            val reading = NutritionLabelParser.parse(lines)
            val expected = parsingCase.getValue("expected").jsonObject

            val expectedMeasure = expected.getValue("measure").let { if (it is JsonNull) null else FoodMeasure.fromRawValue(it.jsonPrimitive.content) }
            assertEquals(name, expectedMeasure, reading.measure)
            assertOptionalEquals(name, expected.optionalDouble("energyKJ"), reading.energyKJ)
            assertOptionalEquals(name, expected.optionalDouble("caloriesPerHundredGrams"), reading.caloriesPerHundredGrams)
            assertOptionalEquals(name, expected.optionalDouble("fat"), reading.fat)
            assertOptionalEquals(name, expected.optionalDouble("fatSaturated"), reading.fatSaturated)
            assertOptionalEquals(name, expected.optionalDouble("fatUnsaturatedFattyAcids"), reading.fatUnsaturatedFattyAcids)
            assertOptionalEquals(name, expected.optionalDouble("carbohydrate"), reading.carbohydrate)
            assertOptionalEquals(name, expected.optionalDouble("carbohydratePureSugar"), reading.carbohydratePureSugar)
            assertOptionalEquals(name, expected.optionalDouble("fiber"), reading.fiber)
            assertOptionalEquals(name, expected.optionalDouble("protein"), reading.protein)
            assertOptionalEquals(name, expected.optionalDouble("salt"), reading.salt)
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

    private fun JsonObject.optionalDouble(key: String): Double? = getValue(key).let { element: JsonElement -> if (element is JsonNull) null else element.jsonPrimitive.double }

    private companion object {
        const val ACCURACY = 1e-9
    }
}
