package antoni.kalorie.features.addfoodsheet

import antoni.kalorie.core.models.FoodItemDomain
import antoni.kalorie.core.models.FoodItemFormPhoto
import antoni.kalorie.core.models.FoodItemKind
import antoni.kalorie.core.models.FoodMeasure
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class FoodItemFormInputTest {

    @Test
    fun asFoodItemDomain_forNewItem_writesHundredAsPackageWeightBecauseTheUserNoLongerEntersIt() {
        val result = FoodItemFormInput(measure = FoodMeasure.MILLILITRES).asFoodItemDomain()

        assertEquals(100.0, result.weight, 0.0)
        assertEquals(FoodMeasure.MILLILITRES, result.measure)
    }

    @Test
    fun initFromItem_keepsStoredPackageWeightUntouchedAcrossARoundTrip() {
        val sut = FoodItemFormInput.from(makeItem(weight = 1500.0))

        assertEquals(1500.0, sut.weightOfProduct, 0.0)
        assertEquals(1500.0, sut.asFoodItemDomain().weight, 0.0)
    }

    @Test
    fun initFromItem_preservesAlcoholByVolume() {
        val sut = FoodItemFormInput.from(makeItem(weight = 100.0, alcoholByVolume = 4.4))

        assertEquals(4.4, sut.alcoholByVolume)
    }

    @Test
    fun asFoodItemDomain_withAlcoholByVolumeEntered_writesTheValue() {
        val result = FoodItemFormInput(alcoholByVolume = 4.4).asFoodItemDomain()

        assertEquals(4.4, result.alcoholByVolume)
    }

    @Test
    fun asFoodItemDomain_withZeroAlcoholByVolume_writesNull() {
        val result = FoodItemFormInput(alcoholByVolume = 0.0).asFoodItemDomain()

        assertEquals(null, result.alcoholByVolume)
    }

    @Test
    fun asFoodItemDomain_withNoAlcoholByVolume_writesNull() {
        val result = FoodItemFormInput().asFoodItemDomain()

        assertEquals(null, result.alcoholByVolume)
    }

    @Test
    fun initFromItem_withACatalogueItemPhoto_showsItAsAlreadyStored() {
        val sut = FoodItemFormInput.from(makeItem(weight = 100.0).copy(photoUrl = "https://storage.fake/a.jpg"))

        assertEquals(FoodItemFormPhoto.Remote("https://storage.fake/a.jpg"), sut.photo)
    }

    @Test
    fun initFromItem_withAnExternalItemPhoto_ignoresItSoAThirdPartyUrlIsNeverSubmitted() {
        val sut = FoodItemFormInput.from(makeItem(weight = 100.0).copy(kind = FoodItemKind.EXTERNAL, photoUrl = "https://images.openfoodfacts.org/a.jpg"))

        assertEquals(FoodItemFormPhoto.None, sut.photo)
    }

    @Test
    fun asFoodItemDomain_withAStoredPhoto_keepsItsUrl() {
        val result = FoodItemFormInput(photo = FoodItemFormPhoto.Remote("https://storage.fake/a.jpg")).asFoodItemDomain()

        assertEquals("https://storage.fake/a.jpg", result.photoUrl)
    }

    @Test
    fun asFoodItemDomain_withANewLocalPhoto_hasNoUrlUntilItIsUploaded() {
        val result = FoodItemFormInput(photo = FoodItemFormPhoto.Local(byteArrayOf(1))).asFoodItemDomain()

        assertEquals(null, result.photoUrl)
    }

    // MARK: - Helpers

    private fun makeItem(weight: Double, alcoholByVolume: Double? = null): FoodItemDomain = FoodItemDomain(
        id = "12345678",
        kind = FoodItemKind.CATALOGUE,
        czName = "Tvaroh",
        engName = "Cottage cheese",
        weight = weight,
        date = Instant.now(),
        energyKJ = 335.0,
        caloriesPerHundredGrams = 80.0,
        fat = 0.5,
        fatSaturated = 0.3,
        fatUnsaturatedFattyAcids = 0.2,
        carbohydrate = 4.0,
        carbohydratePureSugar = 3.0,
        fiber = 0.0,
        protein = 13.0,
        salt = 0.1,
        portions = emptyList(),
        alcoholByVolume = alcoholByVolume,
    )
}
