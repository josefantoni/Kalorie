package antoni.kalorie.core.nutritionlabelrecognition

import antoni.kalorie.core.models.FoodMeasure
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NutritionLabelParserTest {

    // MARK: - Happy path

    @Test
    fun parse_singleLanguageCzechLabel_fillsMandatoryFields() {
        val reading = NutritionLabelParser.parse(czechLabelLines())

        assertEquals(1550.0, reading.energyKJ)
        assertEquals(370.0, reading.caloriesPerHundredGrams)
        assertEquals(12.0, reading.fat)
        assertEquals(2.0, reading.fatSaturated)
        assertEquals(55.0, reading.carbohydrate)
        assertEquals(5.0, reading.carbohydratePureSugar)
        assertEquals(10.0, reading.protein)
        assertEquals(1.0, reading.salt)
    }

    @Test
    fun parse_singleLanguageCzechLabel_derivesUnsaturatedFromFatMinusSaturated() {
        val reading = NutritionLabelParser.parse(czechLabelLines())

        assertEquals(10.0, reading.fatUnsaturatedFattyAcids)
    }

    @Test
    fun parse_multiLanguageLabel_stillMatchesTheCzechRowsViaTheKeywordDictionary() {
        val reading = NutritionLabelParser.parse(multiLanguageLabelLines())

        assertEquals(1550.0, reading.energyKJ)
        assertEquals(12.0, reading.fat)
        assertEquals(10.0, reading.protein)
    }

    @Test
    fun parse_energyLabelledWithTheShortCzechWord_stillFillsEnergy() {
        val lines = czechLabelLines().filterNot { it.text == "Energetická hodnota" } +
            line("Energie | Energia", 0.1, 0.8, 0.3, 0.05)

        val reading = NutritionLabelParser.parse(lines)

        assertEquals(1550.0, reading.energyKJ)
        assertEquals(370.0, reading.caloriesPerHundredGrams)
    }

    @Test
    fun parse_energyWithKcalInParentheses_stillFillsEnergy() {
        val lines = czechLabelLines().filterNot { it.text == "1550 kJ / 370 kcal" } +
            line("1550 kJ (370 kcal)", energyValueBox)

        val reading = NutritionLabelParser.parse(lines)

        assertEquals(1550.0, reading.energyKJ)
        assertEquals(370.0, reading.caloriesPerHundredGrams)
    }

    @Test
    fun parse_realOilBottleLabelWithCorrectedOrientation_pairsEachRowWithItsOwnValue() {
        val lines = listOf(
            line("Výživové údaje", 0.21913699866068093, 0.6104135301927482, 0.1664751782829379, 0.019514760327717484),
            line("Energie | Energia", 0.22222222345464915, 0.5857558146517318, 0.15762273933101162, 0.018906883777133898),
            line("Tuky", 0.2222222222362467, 0.5712209298352923, 0.049095607939220576, 0.015988372621082214),
            line("- z toho nasycené mastné kyseliny", 0.22388370231062413, 0.5558083735068069, 0.29115941284825564, 0.023413160490611262),
            line("- z toho nasycené mastné kyseliny", 0.2239514884144575, 0.5407699214521204, 0.29126923845348324, 0.02398091743862829),
            line("Sacharidy", 0.22983094534206597, 0.5288171823675967, 0.08814172896127856, 0.0164935418537685),
            line("- z toho cukry", 0.22715342896957263, 0.5167396418724755, 0.1167525706888296, 0.014485832244630847),
            line("Bilkoviny | Bielkoviny", 0.23514211884793865, 0.5014534887261335, 0.19379844867363175, 0.01744185932098874),
            line("Sül |Sol", 0.2350956702815575, 0.4882880430787562, 0.08278023586912339, 0.014702983318813256),
            line("Omega 3 mastné kyseliny", 0.23719810405343456, 0.4639273216049472, 0.24391271197606645, 0.02063915275392081),
            line("Omega 6 mastné kyseliny", 0.24031007646279462, 0.4520348841185754, 0.2403100768935323, 0.017459163590083038),
            line("ve 100 ml", 0.568118140749607, 0.6184254531180005, 0.09373787639422815, 0.01605607025207023),
            line("3404 kJ | 813 kcal", 0.5295583999979884, 0.593978919461669, 0.15535348559182782, 0.016984021852886833),
            line("92 g", 0.6304909569871178, 0.5813492062095239, 0.03875968906194016, 0.014632936507936511),
            line("7 g", 0.6408268731998651, 0.5682043650204249, 0.02842377354859038, 0.014644471898911604),
            line("0g", 0.6408268732063249, 0.5406976745559542, 0.028423773548590492, 0.014534883082859107),
            line("0g", 0.6408268726830691, 0.5276162795341518, 0.02842377354859038, 0.014534883082859107),
            line("0 g", 0.6434108534418512, 0.5145348837405155, 0.02583979214730514, 0.013081395436846943),
            line("0 g", 0.6434108534418512, 0.5014534883916784, 0.02583979214730514, 0.013179447915818931),
            line("7,5 g", 0.627906976344025, 0.4811046510170416, 0.043927648500580396, 0.013190983779846732),
            line("16 g", 0.6330749353462908, 0.4665178572825396, 0.03875968906194027, 0.014632936507936511),
        )

        val reading = NutritionLabelParser.parse(lines)

        assertEquals(3404.0, reading.energyKJ)
        assertEquals(813.0, reading.caloriesPerHundredGrams)
        assertEquals(92.0, reading.fat)
        assertEquals(0.0, reading.carbohydrate)
        assertEquals(0.0, reading.carbohydratePureSugar)
        assertEquals(0.0, reading.protein)
        assertNull(reading.salt)
    }

    @Test
    fun parse_realOilBottleLabel_neverReadsANumberOutOfAnUnrelatedLabel() {
        val lines = listOf(
            line("Výživové údaje", 0.1, 0.95, 0.3, 0.03),
            line("ve 100 ml", 0.6, 0.95, 0.2, 0.03),
            line("Energie | Energia", 0.1, 0.85, 0.3, 0.05),
            line("3 404 kJ | 813 kcal", 0.6, 0.85, 0.3, 0.05),
            line("Tuky", 0.1, 0.75, 0.3, 0.05),
            line("92 g", 0.6, 0.75, 0.15, 0.05),
            line("z toho nasycené mastné kyseliny", 0.1, 0.65, 0.4, 0.05),
            line("7 g", 0.6, 0.65, 0.15, 0.05),
            line("z toho nasycené mastné kyseliny", 0.1, 0.55, 0.4, 0.05),
            line("0 g", 0.6, 0.55, 0.15, 0.05),
            line("Sacharidy", 0.1, 0.45, 0.3, 0.05),
            line("0 g", 0.6, 0.45, 0.15, 0.05),
            line("z toho cukry", 0.1, 0.35, 0.3, 0.05),
            line("0 g", 0.6, 0.35, 0.15, 0.05),
            line("Bílkoviny | Bielkoviny", 0.1, 0.25, 0.3, 0.05),
            line("Omega 3 mastné kyseliny", 0.55, 0.25, 0.3, 0.05),
        )

        val reading = NutritionLabelParser.parse(lines)

        assertEquals(92.0, reading.fat)
        assertEquals(3404.0, reading.energyKJ)
        assertEquals(813.0, reading.caloriesPerHundredGrams)
        assertNull(reading.protein)
        assertEquals(FoodMeasure.MILLILITRES, reading.measure)
    }

    // MARK: - Measure

    @Test
    fun parse_perHundredMillilitreHeader_setsMeasureToMillilitres() {
        val lines = listOf(
            line("Hodnoty na 100 ml", 0.6, 0.9, 0.3, 0.03),
            line("Tuky", 0.1, 0.7, 0.3, 0.05),
            line("12 g", 0.6, 0.7, 0.15, 0.05),
        )

        assertEquals(FoodMeasure.MILLILITRES, NutritionLabelParser.parse(lines).measure)
    }

    @Test
    fun parse_perHundredGramHeader_setsMeasureToGrams() {
        assertEquals(FoodMeasure.GRAMS, NutritionLabelParser.parse(czechLabelLines()).measure)
    }

    @Test
    fun parse_noHeaderButPackageInLitres_setsMeasureToMillilitres() {
        val lines = listOf(line("Balení netto 1 l", 0.1, 0.95, 0.3, 0.03))

        assertEquals(FoodMeasure.MILLILITRES, NutritionLabelParser.parse(lines).measure)
    }

    @Test
    fun parse_headerInGramsWithPackageInMillilitres_headerWins() {
        val lines = czechLabelLines() + line("Hmotnost: 500 ml", 0.1, 0.95, 0.3, 0.03)

        assertEquals(FoodMeasure.GRAMS, NutritionLabelParser.parse(lines).measure)
    }

    @Test
    fun parse_withNothingToGoOn_leavesMeasureNil() {
        val lines = listOf(line("Nějaký nesouvisející text", 0.1, 0.5, 0.3, 0.05))

        assertNull(NutritionLabelParser.parse(lines).measure)
    }

    // MARK: - No per-100g column

    @Test
    fun parse_withNoPerHundredHeader_fillsNoNumericField() {
        val lines = listOf(
            line("Tuky", 0.1, 0.5, 0.2, 0.05),
            line("12 g", 0.6, 0.5, 0.15, 0.05),
        )

        assertNull(NutritionLabelParser.parse(lines).fat)
    }

    // MARK: - Consistency checks

    @Test
    fun parse_whenKJDoesNotMatchKcal_dropsBothEnergyFields() {
        val lines = czechLabelLines().filterNot { it.text.contains("1550") } +
            line("9999 kJ / 370 kcal", energyValueBox)

        val reading = NutritionLabelParser.parse(lines)

        assertNull(reading.energyKJ)
        assertNull(reading.caloriesPerHundredGrams)
    }

    @Test
    fun parse_whenSaturatesExceedFat_dropsSaturatesButKeepsFat() {
        val lines = czechLabelLines().filterNot { it.boundingBox == saturatesValueBox } +
            line("50 g", saturatesValueBox)

        val reading = NutritionLabelParser.parse(lines)

        assertNull(reading.fatSaturated)
        assertEquals(12.0, reading.fat)
    }

    @Test
    fun parse_whenMacrosSumOverAHundredGrams_dropsTheSummedFields() {
        val lines = czechLabelLines().filterNot { it.boundingBox == carbohydrateValueBox } +
            line("95 g", carbohydrateValueBox)

        val reading = NutritionLabelParser.parse(lines)

        assertNull(reading.fat)
        assertNull(reading.carbohydrate)
        assertNull(reading.protein)
    }

    // MARK: - Number formats

    @Test
    fun parse_lessThanValue_parsesTheStatedUpperBoundNotZero() {
        val lines = czechLabelLines().filterNot { it.boundingBox == saturatesValueBox } +
            line("<0,5 g", saturatesValueBox)

        assertEquals(0.5, NutritionLabelParser.parse(lines).fatSaturated)
    }

    // MARK: - Linear format (small-package labels with no table)

    @Test
    fun parse_linearFormatLabel_fillsMacrosViaTheFallback() {
        val lines = listOf(
            line("Výživové údaje na 100 g: Energetická hodnota 303 kJ / 72 kcal.", 0.3, 0.42, 0.6, 0.03),
            line("Tuky 3,3 g z toho nasycené mastné kyseliny 0,6 g, Sacharidy 3,0 g z toho", 0.3, 0.38, 0.6, 0.03),
            line("cukry 2,3 g. Bílkoviny 7,3 g. Sůl 1,6 g.", 0.3, 0.34, 0.6, 0.03),
        )

        val reading = NutritionLabelParser.parse(lines)

        assertEquals(303.0, reading.energyKJ)
        assertEquals(72.0, reading.caloriesPerHundredGrams)
        assertEquals(3.3, reading.fat)
        assertEquals(0.6, reading.fatSaturated)
        assertEquals(3.0, reading.carbohydrate)
        assertEquals(2.3, reading.carbohydratePureSugar)
        assertEquals(7.3, reading.protein)
        assertEquals(1.6, reading.salt)
    }

    @Test
    fun parse_linearFormatLabel_ignoresTheSameKeywordRepeatedEarlierInTheIngredientsList() {
        val lines = listOf(
            line("Složení: rybí očka 50 %, sůl, cukr, jedlá sůl, koření, jedlá sůl, konzervant.", 0.1, 0.6, 0.8, 0.03),
            line("Výživové údaje na 100 g: Energetická hodnota 303 kJ / 72 kcal.", 0.3, 0.42, 0.6, 0.03),
            line("Tuky 3,3 g z toho nasycené mastné kyseliny 0,6 g, Sacharidy 3,0 g z toho", 0.3, 0.38, 0.6, 0.03),
            line("cukry 2,3 g. Bílkoviny 7,3 g. Sůl 1,6 g.", 0.3, 0.34, 0.6, 0.03),
        )

        assertEquals(1.6, NutritionLabelParser.parse(lines).salt)
    }

    @Test
    fun parse_weightLabelAboveALargeValueOnASeparateLine_stillFindsIt() {
        val lines = listOf(
            line("Hmotnost:", 0.1, 0.28, 0.15, 0.02),
            line("200 g", 0.1, 0.22, 0.2, 0.045),
        )

        assertEquals(200.0, NutritionLabelParser.parse(lines).weightOfProduct)
    }

    // MARK: - Saturates rows that also contain a fat keyword

    @Test
    fun parse_readsGermanSaturatesRowAsSaturatesNotFat() {
        val reading = NutritionLabelParser.parse(fatAndSaturatesLines("Fett", "davon gesättigte Fettsäuren"))

        assertEquals(12.0, reading.fat)
        assertEquals(2.0, reading.fatSaturated)
    }

    @Test
    fun parse_readsEnglishSaturatedFatRowAsSaturatesNotFat() {
        val reading = NutritionLabelParser.parse(fatAndSaturatesLines("Fat", "Saturated fat"))

        assertEquals(12.0, reading.fat)
        assertEquals(2.0, reading.fatSaturated)
    }

    @Test
    fun parse_unsaturatedFatRowsNeverOverwriteFatOrSaturates() {
        val unsaturatedLabels = listOf(
            "z toho mononenasycené mastné kyseliny",
            "of which mono-unsaturates",
            "davon einfach ungesättigte Fettsäuren",
            "kwasy tłuszczowe jednonienasycone",
        )

        unsaturatedLabels.forEach { label ->
            val lines = fatAndSaturatesLines("Fat", "Saturates") + line(label, 0.1, 0.6, 0.4, 0.05) + line("5 g", 0.6, 0.6, 0.15, 0.05)

            val reading = NutritionLabelParser.parse(lines)

            assertEquals(label, 12.0, reading.fat)
            assertEquals(label, 2.0, reading.fatSaturated)
        }
    }

    // MARK: - Package weight

    @Test
    fun parse_findsPackageWeightNextToAWeightKeyword() {
        val lines = czechLabelLines() + line("Hmotnost: 250 g", 0.1, 0.95, 0.3, 0.03)

        assertEquals(250.0, NutritionLabelParser.parse(lines).weightOfProduct)
    }

    @Test
    fun parse_convertsKilogramsToGrams() {
        val lines = czechLabelLines() + line("Balení netto 1 kg", 0.1, 0.95, 0.3, 0.03)

        assertEquals(1000.0, NutritionLabelParser.parse(lines).weightOfProduct)
    }

    // MARK: - Fixtures

    private val saturatesValueBox = NormalizedRect(0.6, 0.65, 0.15, 0.05)
    private val carbohydrateValueBox = NormalizedRect(0.6, 0.55, 0.15, 0.05)
    private val energyValueBox = NormalizedRect(0.6, 0.8, 0.3, 0.05)

    private fun line(text: String, x: Double, y: Double, width: Double, height: Double) =
        RecognizedTextLine(text, NormalizedRect(x, y, width, height))

    private fun line(text: String, box: NormalizedRect) = RecognizedTextLine(text, box)

    private fun czechLabelLines(): List<RecognizedTextLine> = listOf(
        line("Nutriční hodnoty na 100 g", 0.6, 0.9, 0.3, 0.03),
        line("Energetická hodnota", 0.1, 0.8, 0.3, 0.05),
        line("1550 kJ / 370 kcal", energyValueBox),
        line("Tuky", 0.1, 0.7, 0.3, 0.05),
        line("12 g", 0.6, 0.7, 0.15, 0.05),
        line("z toho nasycené mastné kyseliny", 0.1, 0.65, 0.4, 0.05),
        line("2 g", saturatesValueBox),
        line("Sacharidy", 0.1, 0.55, 0.3, 0.05),
        line("55 g", carbohydrateValueBox),
        line("z toho cukry", 0.1, 0.5, 0.3, 0.05),
        line("5 g", 0.6, 0.5, 0.15, 0.05),
        line("Bílkoviny", 0.1, 0.4, 0.3, 0.05),
        line("10 g", 0.6, 0.4, 0.15, 0.05),
        line("Sůl", 0.1, 0.3, 0.3, 0.05),
        line("1 g", 0.6, 0.3, 0.15, 0.05),
    )

    private fun fatAndSaturatesLines(fatLabel: String, saturatesLabel: String): List<RecognizedTextLine> = listOf(
        line("Nutrition facts per 100 g", 0.6, 0.9, 0.3, 0.03),
        line("Energy", 0.1, 0.8, 0.3, 0.05),
        line("1550 kJ / 370 kcal", energyValueBox),
        line(fatLabel, 0.1, 0.7, 0.3, 0.05),
        line("12 g", 0.6, 0.7, 0.15, 0.05),
        line(saturatesLabel, 0.1, 0.65, 0.4, 0.05),
        line("2 g", saturatesValueBox),
    )

    private fun multiLanguageLabelLines(): List<RecognizedTextLine> = listOf(
        line("100 g", 0.6, 0.9, 0.15, 0.03),
        line("Energetická hodnota / Wartość energetyczna / Brennwert", 0.1, 0.8, 0.4, 0.05),
        line("1550 kJ / 370 kcal", energyValueBox),
        line("Tuky / Tłuszcz / Fett", 0.1, 0.7, 0.4, 0.05),
        line("12 g", 0.6, 0.7, 0.15, 0.05),
        line("Bílkoviny / Białko / Eiweiß", 0.1, 0.4, 0.4, 0.05),
        line("10 g", 0.6, 0.4, 0.15, 0.05),
    )
}
