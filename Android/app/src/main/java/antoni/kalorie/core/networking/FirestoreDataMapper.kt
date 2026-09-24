package antoni.kalorie.core.networking

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

object FirestoreDataMapper {

    // MARK: - Properties

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    // MARK: - Functions

    fun <T> decode(data: Map<String, Any?>, serializer: KSerializer<T>): T =
        json.decodeFromJsonElement(serializer, toJsonElement(data))

    fun <T> encode(item: T, serializer: KSerializer<T>): Map<String, Any?> {
        val element = json.encodeToJsonElement(serializer, item)
        val objectElement = element as? JsonObject ?: error("Firestore documents must encode to a JSON object")
        return objectElement.mapValues { toFirestoreValue(it.value) }
    }

    // MARK: - Private

    private fun toJsonElement(value: Any?): JsonElement = when (value) {
        null -> JsonNull
        is String -> JsonPrimitive(value)
        is Boolean -> JsonPrimitive(value)
        is Number -> JsonPrimitive(value)
        is Map<*, *> -> JsonObject(value.entries.associate { (key, item) -> key.toString() to toJsonElement(item) })
        is List<*> -> JsonArray(value.map(::toJsonElement))
        else -> error("Unsupported Firestore value: ${value::class}")
    }

    private fun toFirestoreValue(element: JsonElement): Any? = when (element) {
        is JsonNull -> null
        is JsonObject -> element.mapValues { toFirestoreValue(it.value) }
        is JsonArray -> element.map(::toFirestoreValue)
        is JsonPrimitive -> when {
            element.isString -> element.content
            element.content == "true" || element.content == "false" -> element.content.toBoolean()
            else -> element.content.toLongOrNull() ?: element.content.toDouble()
        }
    }
}
