package antoni.kalorie.features.addfoodsheet

import antoni.kalorie.core.models.FoodItemDomain
import antoni.kalorie.core.models.FoodItemKind
import antoni.kalorie.core.models.FoodMeasure
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class FoodItemFormInputTest {

    // MARK: - Measure and package weight

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

    // MARK: - Helpers

    private fun makeItem(weight: Double): FoodItemDomain = FoodItemDomain(
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
    )
}
