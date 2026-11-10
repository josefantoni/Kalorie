import antoni.kalorie.macrokit.ActivityLevel
import antoni.kalorie.macrokit.basalMetabolicRate
import antoni.kalorie.macrokit.goalPercentChanges
import antoni.kalorie.macrokit.goalVariants
import antoni.kalorie.macrokit.gramsKcalDiffersFromGoal
import antoni.kalorie.macrokit.referenceKcal
import antoni.kalorie.macrokit.roundedToHundred
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GoalsTest {
    @Test
    fun activityFactorsAreTheHarrisBenedictMultipliers() {
        assertEquals(
            listOf(1.2, 1.375, 1.55, 1.725, 1.9),
            ActivityLevel.entries.map { it.factor },
        )
    }

    @Test
    fun variantsAreTenFivePercentStepsAroundTheExpenditure() {
        val variants = goalVariants(basalRate = 1500.0, expenditure = 2000.0)
        assertEquals(goalPercentChanges, variants.map { it.percentChange })
        assertEquals(listOf(1800, 1900, 2000, 2100, 2200), variants.map { it.kcal })
        assertTrue(variants.none { it.isRaisedToBasalRate })
    }

    @Test
    fun variantBelowTheBasalRateIsRaisedToIt() {
        val variants = goalVariants(basalRate = 1950.0, expenditure = 2000.0)
        assertEquals(listOf(1950, 1950, 2000, 2100, 2200), variants.map { it.kcal })
        assertEquals(listOf(true, true, false, false, false), variants.map { it.isRaisedToBasalRate })
    }

    @Test
    fun referenceKcalIsTheLightActivityBmrOfTheReferencePersonRoundedToHundreds() {
        assertEquals(1767.5, basalMetabolicRate(true, 80.0, 178.0, 30), 1e-9)
        assertEquals(1370.25, basalMetabolicRate(false, 65.0, 165.0, 30), 1e-9)
        assertEquals(2400, referenceKcal(true))
        assertEquals(1900, referenceKcal(false))
    }

    @Test
    fun roundedToHundredSumsToExactlyOneHundred() {
        assertEquals(listOf(20.0, 50.0, 30.0), roundedToHundred(listOf(20.0, 50.0, 30.0)))
        val rounded = roundedToHundred(listOf(33.34, 33.33, 33.33))
        assertEquals(100.0, rounded.sum(), 0.0)
        assertEquals(listOf(34.0, 33.0, 33.0), rounded)
    }

    @Test
    fun roundedToHundredGivesTheMissingUnitsToTheLargestRemainders() {
        assertEquals(listOf(11.0, 59.0, 30.0), roundedToHundred(listOf(10.9, 58.8, 30.3)))
    }

    @Test
    fun gramsKcalWithinTheWholeGramRoundingIsNotADifference() {
        assertFalse(gramsKcalDiffersFromGoal(gramsKcal = 2003.0, goalKcal = 2000.0))
        assertFalse(gramsKcalDiffersFromGoal(gramsKcal = 1990.0, goalKcal = 2000.0))
        assertFalse(gramsKcalDiffersFromGoal(gramsKcal = 2010.0, goalKcal = 2000.0))
    }

    @Test
    fun gramsKcalBeyondTenKcalIsADifference() {
        assertTrue(gramsKcalDiffersFromGoal(gramsKcal = 2011.0, goalKcal = 2000.0))
        assertTrue(gramsKcalDiffersFromGoal(gramsKcal = 1989.0, goalKcal = 2000.0))
    }
}
