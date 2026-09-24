package antoni.kalorie

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

object FixtureLoader {

    // MARK: - Functions

    fun load(name: String): JsonObject {
        val stream = checkNotNull(javaClass.classLoader?.getResourceAsStream("$name.json")) { "Missing fixture $name" }
        return Json.parseToJsonElement(stream.bufferedReader().use { it.readText() }) as JsonObject
    }
}
