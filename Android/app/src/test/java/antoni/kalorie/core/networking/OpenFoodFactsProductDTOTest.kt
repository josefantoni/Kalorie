package antoni.kalorie.core.networking

import antoni.kalorie.FixtureLoader
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class OpenFoodFactsProductDTOTest {

    // MARK: - Tests

    @Test
    fun asDomain_matchesSharedFixtureCases() {
        val fixture = FixtureLoader.load("open-food-facts-mapping-cases")
        for (mappingCase in fixture.getValue("cases").jsonArray.map { it.jsonObject }) {
            val name = mappingCase.getValue("name").jsonPrimitive.content
            val dto = Json.decodeFromJsonElement(OpenFoodFactsProductDTO.serializer(), mappingCase.getValue("product"))
            val item = dto.asDomain()
            val expected = mappingCase.getValue("expected")
            if (expected is JsonNull) {
                assertNull(name, item)
                continue
            }
            val fields = expected.jsonObject
            assertNotNull("$name: expected an item, got null", item)
            checkNotNull(item)
            assertEquals(name, fields.getValue("id").jsonPrimitive.content, item.id)
            assertEquals(name, fields.getValue("czName").jsonPrimitive.content, item.czName)
            assertEquals(name, fields.getValue("engName").jsonPrimitive.content, item.engName)
            assertEquals(name, fields.double("weight"), item.weight, ACCURACY)
            assertEquals(name, fields.double("energyKJ"), item.energyKJ, ACCURACY)
            assertEquals(name, fields.double("caloriesPerHundredGrams"), item.caloriesPerHundredGrams, ACCURACY)
            assertEquals(name, fields.double("fat"), item.fat, ACCURACY)
            assertEquals(name, fields.double("fatSaturated"), item.fatSaturated ?: Double.NaN, ACCURACY)
            assertEquals(name, fields.double("fatUnsaturatedFattyAcids"), item.fatUnsaturatedFattyAcids, ACCURACY)
            assertEquals(name, fields.double("carbohydrate"), item.carbohydrate, ACCURACY)
            assertEquals(name, fields.double("carbohydratePureSugar"), item.carbohydratePureSugar, ACCURACY)
            assertEquals(name, fields.double("fiber"), item.fiber ?: Double.NaN, ACCURACY)
            assertEquals(name, fields.double("protein"), item.protein, ACCURACY)
            assertEquals(name, fields.double("salt"), item.salt, ACCURACY)
        }
    }

    // MARK: - Helpers

    private fun JsonObject.double(key: String): Double = getValue(key).jsonPrimitive.double

    private companion object {
        const val ACCURACY = 1e-9
    }
}
