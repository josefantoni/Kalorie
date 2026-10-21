package antoni.kalorie.core.networking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppConfigDTOTest {

    // MARK: - Tests

    @Test
    fun decode_readsBothStringFields() {
        val dto = decode(mapOf("min_version_ios" to "1.0.1", "min_version_android" to "2.0"))
        assertEquals("1.0.1", dto.minVersionIos)
        assertEquals("2.0", dto.minVersionAndroid)
    }

    @Test
    fun decode_whenFieldHasWrongType_decodesAsNullInsteadOfThrowing() {
        val dto = decode(mapOf("min_version_android" to 1.5, "min_version_ios" to "1.0"))
        assertNull("a mistyped console value must switch the gate off, not leave a blocked client blocked", dto.minVersionAndroid)
        assertEquals("one bad field must not hide the other", "1.0", dto.minVersionIos)
    }

    @Test
    fun decode_whenUnrelatedFieldHasNoJsonForm_stillReadsTheVersions() {
        val dto = decode(mapOf("min_version_android" to "2.0", "updated_at" to Any()))
        assertEquals("an extra console field of an exotic type must not switch the gate off on Android only", "2.0", dto.minVersionAndroid)
    }

    @Test
    fun decode_whenFieldsAreAbsent_decodesAsNull() {
        val dto = decode(emptyMap())
        assertNull(dto.minVersionIos)
        assertNull(dto.minVersionAndroid)
    }

    // MARK: - Helpers

    private fun decode(data: Map<String, Any?>): AppConfigDTO = FirestoreDataMapper.decode(data, AppConfigDTO.serializer())
}
