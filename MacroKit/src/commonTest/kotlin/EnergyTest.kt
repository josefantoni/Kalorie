import antoni.kalorie.macrokit.basalMetabolicRate
import antoni.kalorie.macrokit.energyShares
import antoni.kalorie.macrokit.gramsFromKcal
import antoni.kalorie.macrokit.kcalFromGrams
import kotlin.test.Test
import kotlin.test.assertEquals

class EnergyTest {
    @Test
    fun maleBmrMatchesOwnerSanityCheck() {
        assertEquals(1925.0, basalMetabolicRate(true, 92.0, 188.0, 35), 1.0)
    }

    @Test
    fun femaleOffsetIsMinus161() {
        assertEquals(basalMetabolicRate(true, 70.0, 170.0, 30) - 166, basalMetabolicRate(false, 70.0, 170.0, 30), 1e-9)
    }

    @Test
    fun sharesAreEnergyNotGrams() {
        val shares = energyShares(protein = 0.0, carbohydrate = 0.0, fat = 12.0)
        assertEquals(1.0, shares[2], 1e-9)
        val mixed = energyShares(protein = 20.0, carbohydrate = 30.0, fat = 12.0)
        assertEquals(1.0, mixed.sum(), 1e-9)
        assertEquals(108.0 / 308.0, mixed[2], 1e-9)
    }

    @Test
    fun gramsRoundTripThroughKcal() {
        val g = gramsFromKcal(2000.0, 30.0, isFat = true)
        assertEquals(600.0, kcalFromGrams(0.0, 0.0, g), 1e-9)
    }
}
