package antoni.kalorie.core.networking

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonPrimitive

@Serializable
data class AppConfigDTO(
    @Serializable(with = StringOrNullSerializer::class)
    @SerialName("min_version_ios") val minVersionIos: String? = null,
    @Serializable(with = StringOrNullSerializer::class)
    @SerialName("min_version_android") val minVersionAndroid: String? = null,
)

object StringOrNullSerializer : KSerializer<String?> {

    // MARK: - Properties

    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("StringOrNull", PrimitiveKind.STRING)

    // MARK: - Functions

    override fun deserialize(decoder: Decoder): String? {
        val element = (decoder as JsonDecoder).decodeJsonElement()
        return (element as? JsonPrimitive)?.takeIf { it.isString }?.content
    }

    override fun serialize(encoder: Encoder, value: String?) {
        if (value == null) encoder.encodeNull() else encoder.encodeString(value)
    }
}
