package antoni.kalorie.textkit

import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.contentOrNull
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
    fun localizedPortionName_matchesSharedFixture() {
        val cases = fixture.getValue("portionName").jsonArray
        assertTrue(cases.isNotEmpty())
        for (case in cases) {
            val input = case.jsonObject.getValue("input").jsonPrimitive.content
            val language = case.jsonObject.getValue("language").jsonPrimitive.content
            val expected = case.jsonObject.getValue("expected").jsonPrimitive.content
            assertEquals(expected, localizedPortionName(input, language), "$input ($language)")
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

    @Test
    fun sanitizeDecimalInput_matchesSharedFixture() {
        val cases = fixture.getValue("decimalInputSanitize").jsonArray
        assertTrue(cases.isNotEmpty())
        for (case in cases) {
            val input = case.jsonObject.getValue("input").jsonPrimitive.content
            val expected = case.jsonObject.getValue("expected").jsonPrimitive.content
            assertEquals(expected, sanitizeDecimalInput(input), input)
        }
    }

    @Test
    fun parseDecimalInput_matchesSharedFixture() {
        val cases = fixture.getValue("decimalInputParse").jsonArray
        assertTrue(cases.isNotEmpty())
        for (case in cases) {
            val input = case.jsonObject.getValue("input").jsonPrimitive.content
            val expected = case.jsonObject.getValue("expected").jsonPrimitive.doubleOrNull
            assertEquals(expected, parseDecimalInput(input), input)
        }
    }

    @Test
    fun servingQuantity_matchesSharedFixture() {
        val cases = fixture.getValue("servingQuantity").jsonArray
        assertTrue(cases.isNotEmpty())
        for (case in cases) {
            val number = case.jsonObject["number"]?.jsonPrimitive?.doubleOrNull
            val text = case.jsonObject["text"]?.jsonPrimitive?.contentOrNull
            val expected = case.jsonObject.getValue("expected").jsonPrimitive.doubleOrNull
            assertEquals(expected, servingQuantity(number, text), "$number $text")
        }
    }

    @Test
    fun servingQuantityUnit_matchesSharedFixture() {
        val cases = fixture.getValue("servingQuantityUnit").jsonArray
        assertTrue(cases.isNotEmpty())
        for (case in cases) {
            val raw = case.jsonObject["raw"]?.jsonPrimitive?.contentOrNull
            val expected = case.jsonObject.getValue("expected").jsonPrimitive.contentOrNull
            assertEquals(expected, servingQuantityUnit(raw), "$raw")
        }
    }

    private fun JsonArray.strings(): List<String> = map { it.jsonPrimitive.content }
}
