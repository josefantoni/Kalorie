package antoni.kalorie.core.models

import antoni.kalorie.BuildConfig
import antoni.kalorie.FixtureLoader
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class AppVersionTest {

    // MARK: - Tests

    @Test
    fun parse_matchesSharedParseFixtureCases() {
        val fixture = FixtureLoader.load("app-version-comparison")
        for (parseCase in fixture.getValue("parse").jsonArray.map { it.jsonObject }) {
            val name = parseCase.getValue("name").jsonPrimitive.content
            val version = parseCase.getValue("version").jsonPrimitive.content
            assertEquals(name, parseCase.getValue("valid").jsonPrimitive.boolean, AppVersion.parse(version) != null)
        }
    }

    @Test
    fun compare_matchesSharedComparisonFixtureCases() {
        val fixture = FixtureLoader.load("app-version-comparison")
        for (comparisonCase in fixture.getValue("compare").jsonArray.map { it.jsonObject }) {
            val name = comparisonCase.getValue("name").jsonPrimitive.content
            val lhs = checkNotNull(AppVersion.parse(comparisonCase.getValue("lhs").jsonPrimitive.content)) { name }
            val rhs = checkNotNull(AppVersion.parse(comparisonCase.getValue("rhs").jsonPrimitive.content)) { name }
            val expected = comparisonCase.getValue("expected").jsonPrimitive.int
            assertEquals(name, expected, lhs.compareTo(rhs).coerceIn(-1, 1))
            assertEquals(name, expected == 0, lhs == rhs)
            if (expected == 0) assertEquals(name, lhs.hashCode(), rhs.hashCode())
        }
    }

    @Test
    fun buildConfigVersionName_parses() {
        assertNotNull(
            "The gate is silently off when the real version name does not parse",
            AppVersion.parse(BuildConfig.VERSION_NAME),
        )
    }
}
