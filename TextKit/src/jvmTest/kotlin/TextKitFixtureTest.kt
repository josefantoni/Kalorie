package antoni.kalorie.textkit

import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TextKitFixtureTest {

    private val fixture: JsonObject =
        Json.parseToJsonElement(File("fixtures/text-kit-cases.json").readText()).jsonObject

    @Test
    fun foldDiacritics_matchesSharedFixture() {
        val cases = fixture.getValue("foldDiacritics").jsonArray
        assertTrue(cases.isNotEmpty())
        for (case in cases) {
            val input = case.jsonObject.getValue("input").jsonPrimitive.content
            val expected = case.jsonObject.getValue("expected").jsonPrimitive.content
            assertEquals(expected, foldDiacritics(input), input)
        }
    }

    @Test
    fun searchTerms_matchesSharedFixture() {
        val cases = fixture.getValue("searchTerms").jsonArray
        assertTrue(cases.isNotEmpty())
        for (case in cases) {
            val input = case.jsonObject.getValue("input").jsonPrimitive.content
            val expected = case.jsonObject.getValue("expected").jsonArray.strings()
            assertEquals(expected, searchTerms(input), input)
        }
    }

    private fun JsonArray.strings(): List<String> = map { it.jsonPrimitive.content }
}
