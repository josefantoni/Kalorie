package antoni.kalorie.core.networking

import antoni.kalorie.core.models.FoodItemDomain
import antoni.kalorie.core.models.FoodItemKind
import antoni.kalorie.core.models.FoodMeasure
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class FoodItemDTOTest {

    // MARK: - Tests

    @Test
    fun asDomain_whenMeasureUnitIsAbsent_defaultsToGrams() {
        val json = encodedJSON().apply { remove("measure_unit") }
        val dto = decode(json)
        assertEquals("every catalogue item written before this design must stay grams", FoodMeasure.GRAMS, dto.asDomain().measure)
    }

    @Test
    fun asDomain_whenMeasureUnitIsUnknown_defaultsToGramsWithoutThrowing() {
        val json = encodedJSON().apply { put("measure_unit", "litres") }
        val dto = decode(json)
        assertEquals("one bad document must not fail a whole search", FoodMeasure.GRAMS, dto.asDomain().measure)
    }

    @Test
    fun asDomain_whenMeasureUnitIsMillilitres_preservesIt() {
        val json = encodedJSON().apply { put("measure_unit", "millilitres") }
        val dto = decode(json)
        assertEquals(FoodMeasure.MILLILITRES, dto.asDomain().measure)
    }

    @Test
    fun asDomain_whenAlcoholByVolumeIsAbsent_isNull() {
        val json = encodedJSON().apply { remove("alcohol_by_volume") }
        val dto = decode(json)
        assertEquals("a document without the key must decode, not throw", null, dto.asDomain().alcoholByVolume)
    }

    @Test
    fun alcoholByVolume_survivesRoundTrip() {
        val json = FirestoreDataMapper.encode(FoodItemDTO(makeItem(alcoholByVolume = 4.4)), FoodItemDTO.serializer())
        val dto = decode(json)
        assertEquals(4.4, dto.asDomain().alcoholByVolume)
    }

    @Test
    fun asDomain_whenPhotoUrlIsAbsent_isNull() {
        val json = encodedJSON().apply { remove("photo_url") }
        val dto = decode(json)
        assertEquals("a document without the key must decode, not throw", null, dto.asDomain().photoUrl)
    }

    @Test
    fun photoUrl_survivesRoundTrip() {
        val json = FirestoreDataMapper.encode(FoodItemDTO(makeItem(photoUrl = "https://storage.fake/a.jpg")), FoodItemDTO.serializer())
        val dto = decode(json)
        assertEquals("https://storage.fake/a.jpg", dto.asDomain().photoUrl)
    }

    // MARK: - Helpers

    private fun encodedJSON(): MutableMap<String, Any?> = FirestoreDataMapper.encode(FoodItemDTO(makeItem()), FoodItemDTO.serializer()).toMutableMap()

    private fun decode(json: Map<String, Any?>): FoodItemDTO = FirestoreDataMapper.decode(json, FoodItemDTO.serializer())

    private fun makeItem(alcoholByVolume: Double? = null, photoUrl: String? = null): FoodItemDomain = FoodItemDomain(
        id = "12345678",
        kind = FoodItemKind.CATALOGUE,
        czName = "Mléko",
        engName = "Milk",
        weight = 1000.0,
        date = Instant.now(),
        energyKJ = 270.0,
        caloriesPerHundredGrams = 64.0,
        fat = 3.5,
        fatSaturated = 2.1,
        fatUnsaturatedFattyAcids = 1.0,
        carbohydrate = 4.8,
        carbohydratePureSugar = 4.8,
        fiber = 0.0,
        protein = 3.2,
        salt = 0.1,
        alcoholByVolume = alcoholByVolume,
        photoUrl = photoUrl,
    )
}
