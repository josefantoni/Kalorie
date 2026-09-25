//
//  NutritionLabelParserTests.swift
//  KalorieTests
//
//  Created by Josef Antoni on 13.09.2026.
//

import XCTest
@testable import Kalorie

final class NutritionLabelParserTests: XCTestCase {

    // MARK: - Happy path

    func test_parse_singleLanguageCzechLabel_fillsMandatoryFields() {
        let reading = NutritionLabelParser.parse(lines: czechLabelLines())

        XCTAssertEqual(reading.energyKJ, 1550)
        XCTAssertEqual(reading.caloriesPerHundredGrams, 370)
        XCTAssertEqual(reading.fat, 12)
        XCTAssertEqual(reading.fatSaturated, 2)
        XCTAssertEqual(reading.carbohydrate, 55)
        XCTAssertEqual(reading.carbohydratePureSugar, 5)
        XCTAssertEqual(reading.protein, 10)
        XCTAssertEqual(reading.salt, 1)
    }

    func test_parse_singleLanguageCzechLabel_derivesUnsaturatedFromFatMinusSaturated() {
        let reading = NutritionLabelParser.parse(lines: czechLabelLines())
        XCTAssertEqual(reading.fatUnsaturatedFattyAcids, 10, "unsaturated is not on this label, so it must be derived as fat - saturated")
    }

    func test_parse_multiLanguageLabel_stillMatchesTheCzechRowsViaTheKeywordDictionary() {
        let reading = NutritionLabelParser.parse(lines: multiLanguageLabelLines())

        XCTAssertEqual(reading.energyKJ, 1550)
        XCTAssertEqual(reading.fat, 12)
        XCTAssertEqual(reading.protein, 10)
    }

    func test_parse_energyWithKcalInParentheses_stillFillsEnergy() {
        var lines = czechLabelLines()
        lines.removeAll { $0.text == "1550 kJ / 370 kcal" }
        lines.append(RecognizedTextLine(text: "1550 kJ (370 kcal)", boundingBox: energyValueBox))
        let reading = NutritionLabelParser.parse(lines: lines)
        XCTAssertEqual(reading.energyKJ, 1550)
        XCTAssertEqual(reading.caloriesPerHundredGrams, 370)
    }

    func test_parse_energyLabelledWithTheShortCzechWord_stillFillsEnergy() {
        var lines = czechLabelLines()
        lines.removeAll { $0.text == "Energetická hodnota" }
        lines.append(RecognizedTextLine(text: "Energie | Energia", boundingBox: CGRect(x: 0.1, y: 0.8, width: 0.3, height: 0.05)))
        let reading = NutritionLabelParser.parse(lines: lines)
        XCTAssertEqual(reading.energyKJ, 1550, "real labels commonly say just \"Energie\", not the long-form \"energetická hodnota\"")
        XCTAssertEqual(reading.caloriesPerHundredGrams, 370)
    }

    func test_parse_realOilBottleLabelWithCorrectedOrientation_pairsEachRowWithItsOwnValue() {
        // Transcribed verbatim from the on-device Vision output once image orientation was fixed
        // (a prior bug had Vision reading the whole photo sideways). With correct geometry, "Energie
        // | Energia" and its value line overlap enough that groupIntoRows' *accumulated* row bounding
        // box grew tall enough to also absorb "92 g" — stranding "Tuky" to instead pair with "7 g"
        // (the saturates row below it) by the same flawed logic. Real symptom this reproduced:
        // the UI showed fat as 7, not 92, and every other row shifted by the same mechanism.
        let lines = [
            RecognizedTextLine(text: "Výživové údaje", boundingBox: CGRect(x: 0.21913699866068093, y: 0.6104135301927482, width: 0.1664751782829379, height: 0.019514760327717484)),
            RecognizedTextLine(text: "Energie | Energia", boundingBox: CGRect(x: 0.22222222345464915, y: 0.5857558146517318, width: 0.15762273933101162, height: 0.018906883777133898)),
            RecognizedTextLine(text: "Tuky", boundingBox: CGRect(x: 0.2222222222362467, y: 0.5712209298352923, width: 0.049095607939220576, height: 0.015988372621082214)),
            RecognizedTextLine(text: "- z toho nasycené mastné kyseliny", boundingBox: CGRect(x: 0.22388370231062413, y: 0.5558083735068069, width: 0.29115941284825564, height: 0.023413160490611262)),
            RecognizedTextLine(text: "- z toho nasycené mastné kyseliny", boundingBox: CGRect(x: 0.2239514884144575, y: 0.5407699214521204, width: 0.29126923845348324, height: 0.02398091743862829)),
            RecognizedTextLine(text: "Sacharidy", boundingBox: CGRect(x: 0.22983094534206597, y: 0.5288171823675967, width: 0.08814172896127856, height: 0.0164935418537685)),
            RecognizedTextLine(text: "- z toho cukry", boundingBox: CGRect(x: 0.22715342896957263, y: 0.5167396418724755, width: 0.1167525706888296, height: 0.014485832244630847)),
            RecognizedTextLine(text: "Bilkoviny | Bielkoviny", boundingBox: CGRect(x: 0.23514211884793865, y: 0.5014534887261335, width: 0.19379844867363175, height: 0.01744185932098874)),
            RecognizedTextLine(text: "Sül |Sol", boundingBox: CGRect(x: 0.2350956702815575, y: 0.4882880430787562, width: 0.08278023586912339, height: 0.014702983318813256)),
            RecognizedTextLine(text: "Omega 3 mastné kyseliny", boundingBox: CGRect(x: 0.23719810405343456, y: 0.4639273216049472, width: 0.24391271197606645, height: 0.02063915275392081)),
            RecognizedTextLine(text: "Omega 6 mastné kyseliny", boundingBox: CGRect(x: 0.24031007646279462, y: 0.4520348841185754, width: 0.2403100768935323, height: 0.017459163590083038)),
            RecognizedTextLine(text: "ve 100 ml", boundingBox: CGRect(x: 0.568118140749607, y: 0.6184254531180005, width: 0.09373787639422815, height: 0.01605607025207023)),
            RecognizedTextLine(text: "3404 kJ | 813 kcal", boundingBox: CGRect(x: 0.5295583999979884, y: 0.593978919461669, width: 0.15535348559182782, height: 0.016984021852886833)),
            RecognizedTextLine(text: "92 g", boundingBox: CGRect(x: 0.6304909569871178, y: 0.5813492062095239, width: 0.03875968906194016, height: 0.014632936507936511)),
            RecognizedTextLine(text: "7 g", boundingBox: CGRect(x: 0.6408268731998651, y: 0.5682043650204249, width: 0.02842377354859038, height: 0.014644471898911604)),
            RecognizedTextLine(text: "0g", boundingBox: CGRect(x: 0.6408268732063249, y: 0.5406976745559542, width: 0.028423773548590492, height: 0.014534883082859107)),
            RecognizedTextLine(text: "0g", boundingBox: CGRect(x: 0.6408268726830691, y: 0.5276162795341518, width: 0.02842377354859038, height: 0.014534883082859107)),
            RecognizedTextLine(text: "0 g", boundingBox: CGRect(x: 0.6434108534418512, y: 0.5145348837405155, width: 0.02583979214730514, height: 0.013081395436846943)),
            RecognizedTextLine(text: "0 g", boundingBox: CGRect(x: 0.6434108534418512, y: 0.5014534883916784, width: 0.02583979214730514, height: 0.013179447915818931)),
            RecognizedTextLine(text: "7,5 g", boundingBox: CGRect(x: 0.627906976344025, y: 0.4811046510170416, width: 0.043927648500580396, height: 0.013190983779846732)),
            RecognizedTextLine(text: "16 g", boundingBox: CGRect(x: 0.6330749353462908, y: 0.4665178572825396, width: 0.03875968906194027, height: 0.014632936507936511))
        ]

        let reading = NutritionLabelParser.parse(lines: lines)

        XCTAssertEqual(reading.energyKJ, 3404)
        XCTAssertEqual(reading.caloriesPerHundredGrams, 813)
        XCTAssertEqual(reading.fat, 92, "\"Tuky\" must pair with the value directly beside it (92 g), not the row below's 7 g")
        XCTAssertEqual(reading.carbohydrate, 0)
        XCTAssertEqual(reading.carbohydratePureSugar, 0)
        XCTAssertEqual(reading.protein, 0, "must read the real 0 g next to it, not a stray digit from an unrelated row")
        XCTAssertNil(reading.salt, "the OCR misread of \"Sůl\" (\"Sül\") matches no salt keyword — left empty rather than guessed")
    }

    func test_parse_realOilBottleLabel_neverReadsANumberOutOfAnUnrelatedLabel() {
        // Transcribed from a real oil bottle: "Energie" (not the long-form "energetická hodnota"),
        // a printing defect that duplicated the "z toho nasycené mastné kyseliny" row (shifting the
        // rows below it by one against the value column, so the protein row's only nearby candidate
        // becomes the unrelated "Omega 3 mastné kyseliny" label below it), and the "Sůl" row's value
        // missing entirely because the table only has as many value rows as *intended* labels.
        let lines = [
            RecognizedTextLine(text: "Výživové údaje", boundingBox: CGRect(x: 0.1, y: 0.95, width: 0.3, height: 0.03)),
            RecognizedTextLine(text: "ve 100 ml", boundingBox: CGRect(x: 0.6, y: 0.95, width: 0.2, height: 0.03)),
            RecognizedTextLine(text: "Energie | Energia", boundingBox: CGRect(x: 0.1, y: 0.85, width: 0.3, height: 0.05)),
            RecognizedTextLine(text: "3 404 kJ | 813 kcal", boundingBox: CGRect(x: 0.6, y: 0.85, width: 0.3, height: 0.05)),
            RecognizedTextLine(text: "Tuky", boundingBox: CGRect(x: 0.1, y: 0.75, width: 0.3, height: 0.05)),
            RecognizedTextLine(text: "92 g", boundingBox: CGRect(x: 0.6, y: 0.75, width: 0.15, height: 0.05)),
            RecognizedTextLine(text: "z toho nasycené mastné kyseliny", boundingBox: CGRect(x: 0.1, y: 0.65, width: 0.4, height: 0.05)),
            RecognizedTextLine(text: "7 g", boundingBox: CGRect(x: 0.6, y: 0.65, width: 0.15, height: 0.05)),
            RecognizedTextLine(text: "z toho nasycené mastné kyseliny", boundingBox: CGRect(x: 0.1, y: 0.55, width: 0.4, height: 0.05)),
            RecognizedTextLine(text: "0 g", boundingBox: CGRect(x: 0.6, y: 0.55, width: 0.15, height: 0.05)),
            RecognizedTextLine(text: "Sacharidy", boundingBox: CGRect(x: 0.1, y: 0.45, width: 0.3, height: 0.05)),
            RecognizedTextLine(text: "0 g", boundingBox: CGRect(x: 0.6, y: 0.45, width: 0.15, height: 0.05)),
            RecognizedTextLine(text: "z toho cukry", boundingBox: CGRect(x: 0.1, y: 0.35, width: 0.3, height: 0.05)),
            RecognizedTextLine(text: "0 g", boundingBox: CGRect(x: 0.6, y: 0.35, width: 0.15, height: 0.05)),
            RecognizedTextLine(text: "Bílkoviny | Bielkoviny", boundingBox: CGRect(x: 0.1, y: 0.25, width: 0.3, height: 0.05)),
            RecognizedTextLine(text: "Omega 3 mastné kyseliny", boundingBox: CGRect(x: 0.55, y: 0.25, width: 0.3, height: 0.05))
        ]
        let reading = NutritionLabelParser.parse(lines: lines)
        XCTAssertEqual(reading.fat, 92, "the unrelated row below it must not stop a value the table already found correctly")
        XCTAssertEqual(reading.energyKJ, 3404, "\"Energie\", the short EU-legal form, must be recognised alongside the long-form \"energetická hodnota\"")
        XCTAssertEqual(reading.caloriesPerHundredGrams, 813)
        XCTAssertNil(reading.protein, "\"Omega 3 mastné kyseliny\" is a label, not a value — its stray digit must never become the protein reading")
        XCTAssertEqual(reading.measure, .millilitres, "the label's own \"ve 100 ml\" header must set the measure, not the default grams")
    }

    // MARK: - Measure

    func test_parse_perHundredMillilitreHeader_setsMeasureToMillilitres() {
        let lines = [
            RecognizedTextLine(text: "Hodnoty na 100 ml", boundingBox: CGRect(x: 0.6, y: 0.9, width: 0.3, height: 0.03)),
            RecognizedTextLine(text: "Tuky", boundingBox: CGRect(x: 0.1, y: 0.7, width: 0.3, height: 0.05)),
            RecognizedTextLine(text: "12 g", boundingBox: CGRect(x: 0.6, y: 0.7, width: 0.15, height: 0.05))
        ]
        let reading = NutritionLabelParser.parse(lines: lines)
        XCTAssertEqual(reading.measure, .millilitres)
    }

    func test_parse_perHundredGramHeader_setsMeasureToGrams() {
        let reading = NutritionLabelParser.parse(lines: czechLabelLines())
        XCTAssertEqual(reading.measure, .grams)
    }

    func test_parse_noHeaderButPackageInLitres_setsMeasureToMillilitres() {
        let lines = [
            RecognizedTextLine(text: "Balení netto 1 l", boundingBox: CGRect(x: 0.1, y: 0.95, width: 0.3, height: 0.03))
        ]
        let reading = NutritionLabelParser.parse(lines: lines)
        XCTAssertEqual(reading.measure, .millilitres, "with no nutrition header to key off, the package line's own unit is the only signal left")
    }

    func test_parse_headerInGramsWithPackageInMillilitres_headerWins() {
        var lines = czechLabelLines()
        lines.append(RecognizedTextLine(text: "Hmotnost: 500 ml", boundingBox: CGRect(x: 0.1, y: 0.95, width: 0.3, height: 0.03)))
        let reading = NutritionLabelParser.parse(lines: lines)
        XCTAssertEqual(reading.measure, .grams, "the nutrition basis the numbers were actually read against always outranks the package line")
    }

    func test_parse_withNothingToGoOn_leavesMeasureNil() {
        let lines = [
            RecognizedTextLine(text: "Nějaký nesouvisející text", boundingBox: CGRect(x: 0.1, y: 0.5, width: 0.3, height: 0.05))
        ]
        let reading = NutritionLabelParser.parse(lines: lines)
        XCTAssertNil(reading.measure)
    }

    // MARK: - No per-100g column

    func test_parse_withNoPerHundredHeader_fillsNoNumericField() {
        let lines = [
            RecognizedTextLine(text: "Tuky", boundingBox: CGRect(x: 0.1, y: 0.5, width: 0.2, height: 0.05)),
            RecognizedTextLine(text: "12 g", boundingBox: CGRect(x: 0.6, y: 0.5, width: 0.15, height: 0.05))
        ]
        let reading = NutritionLabelParser.parse(lines: lines)
        XCTAssertNil(reading.fat, "without a recognisable per-100g header, no numeric field may be filled")
    }

    // MARK: - Consistency checks

    func test_parse_whenKJDoesNotMatchKcal_dropsBothEnergyFields() {
        var lines = czechLabelLines()
        lines.removeAll { $0.text.contains("1550") }
        lines.append(RecognizedTextLine(text: "9999 kJ / 370 kcal", boundingBox: energyValueBox))
        let reading = NutritionLabelParser.parse(lines: lines)
        XCTAssertNil(reading.energyKJ)
        XCTAssertNil(reading.caloriesPerHundredGrams)
    }

    func test_parse_whenSaturatesExceedFat_dropsSaturatesButKeepsFat() {
        var lines = czechLabelLines()
        lines.removeAll { $0.text.contains("2 g") && $0.boundingBox == saturatesValueBox }
        lines.append(RecognizedTextLine(text: "50 g", boundingBox: saturatesValueBox))
        let reading = NutritionLabelParser.parse(lines: lines)
        XCTAssertNil(reading.fatSaturated)
        XCTAssertEqual(reading.fat, 12)
    }

    func test_parse_whenMacrosSumOverAHundredGrams_dropsTheSummedFields() {
        var lines = czechLabelLines()
        lines.removeAll { $0.boundingBox == carbohydrateValueBox }
        lines.append(RecognizedTextLine(text: "95 g", boundingBox: carbohydrateValueBox))
        let reading = NutritionLabelParser.parse(lines: lines)
        XCTAssertNil(reading.fat)
        XCTAssertNil(reading.carbohydrate)
        XCTAssertNil(reading.protein)
    }

    // MARK: - Number formats

    func test_parse_lessThanValue_parsesTheStatedUpperBoundNotZero() {
        var lines = czechLabelLines()
        lines.removeAll { $0.boundingBox == saturatesValueBox }
        lines.append(RecognizedTextLine(text: "<0,5 g", boundingBox: saturatesValueBox))
        let reading = NutritionLabelParser.parse(lines: lines)
        XCTAssertEqual(reading.fatSaturated, 0.5)
    }

    // MARK: - Linear format (small-package labels with no table)

    func test_parse_linearFormatLabel_fillsMacrosViaTheFallback() {
        let lines = [
            RecognizedTextLine(text: "Výživové údaje na 100 g: Energetická hodnota 303 kJ / 72 kcal.", boundingBox: CGRect(x: 0.3, y: 0.42, width: 0.6, height: 0.03)),
            RecognizedTextLine(text: "Tuky 3,3 g z toho nasycené mastné kyseliny 0,6 g, Sacharidy 3,0 g z toho", boundingBox: CGRect(x: 0.3, y: 0.38, width: 0.6, height: 0.03)),
            RecognizedTextLine(text: "cukry 2,3 g. Bílkoviny 7,3 g. Sůl 1,6 g.", boundingBox: CGRect(x: 0.3, y: 0.34, width: 0.6, height: 0.03))
        ]
        let reading = NutritionLabelParser.parse(lines: lines)
        XCTAssertEqual(reading.energyKJ, 303)
        XCTAssertEqual(reading.caloriesPerHundredGrams, 72)
        XCTAssertEqual(reading.fat, 3.3)
        XCTAssertEqual(reading.fatSaturated, 0.6)
        XCTAssertEqual(reading.carbohydrate, 3.0)
        XCTAssertEqual(reading.carbohydratePureSugar, 2.3)
        XCTAssertEqual(reading.protein, 7.3)
        XCTAssertEqual(reading.salt, 1.6)
    }

    func test_parse_linearFormatLabel_ignoresTheSameKeywordRepeatedEarlierInTheIngredientsList() {
        let lines = [
            RecognizedTextLine(text: "Složení: rybí očka 50 %, sůl, cukr, jedlá sůl, koření, jedlá sůl, konzervant.", boundingBox: CGRect(x: 0.1, y: 0.6, width: 0.8, height: 0.03)),
            RecognizedTextLine(text: "Výživové údaje na 100 g: Energetická hodnota 303 kJ / 72 kcal.", boundingBox: CGRect(x: 0.3, y: 0.42, width: 0.6, height: 0.03)),
            RecognizedTextLine(text: "Tuky 3,3 g z toho nasycené mastné kyseliny 0,6 g, Sacharidy 3,0 g z toho", boundingBox: CGRect(x: 0.3, y: 0.38, width: 0.6, height: 0.03)),
            RecognizedTextLine(text: "cukry 2,3 g. Bílkoviny 7,3 g. Sůl 1,6 g.", boundingBox: CGRect(x: 0.3, y: 0.34, width: 0.6, height: 0.03))
        ]
        let reading = NutritionLabelParser.parse(lines: lines)
        XCTAssertEqual(reading.salt, 1.6, "the ingredients list mentions salt three times before the real value; the nutrition-section anchor must skip all of them")
    }

    func test_parse_linearFormatLabel_readsANumberThatEndsAtASentenceStop() {
        let lines = [
            RecognizedTextLine(text: "Výživové údaje na 100 g: Energetická hodnota 303 kJ / 72 kcal. Tuky 3,3. Sacharidy 3,0. Bílkoviny 7,3. Sůl 1,6.", boundingBox: CGRect(x: 0.3, y: 0.42, width: 0.6, height: 0.03))
        ]
        let reading = NutritionLabelParser.parse(lines: lines)
        XCTAssertEqual(reading.fat, 3.3)
        XCTAssertEqual(reading.carbohydrate, 3.0)
        XCTAssertEqual(reading.protein, 7.3)
        XCTAssertEqual(reading.salt, 1.6)
    }

    func test_parse_linearFormatLabel_doesNotReadSulphitesInTheIngredientsAsSalt() {
        let lines = [
            RecognizedTextLine(text: "Ingredients: wheat flour, sulphites (E220).", boundingBox: CGRect(x: 0.1, y: 0.6, width: 0.8, height: 0.03)),
            RecognizedTextLine(text: "Per 100 g: Energy 303 kJ / 72 kcal. Fat 3.3 g. Carbohydrate 3.0 g. Protein 7.3 g. Salt 1.6 g.", boundingBox: CGRect(x: 0.3, y: 0.42, width: 0.6, height: 0.03))
        ]
        let reading = NutritionLabelParser.parse(lines: lines)
        XCTAssertEqual(reading.salt, 1.6)
        XCTAssertEqual(reading.fat, 3.3)
    }

    func test_parse_weightLabelAboveALargeValueOnASeparateLine_stillFindsIt() {
        let lines = [
            RecognizedTextLine(text: "Hmotnost:", boundingBox: CGRect(x: 0.1, y: 0.28, width: 0.15, height: 0.02)),
            RecognizedTextLine(text: "200 g", boundingBox: CGRect(x: 0.1, y: 0.22, width: 0.2, height: 0.045))
        ]
        let reading = NutritionLabelParser.parse(lines: lines)
        XCTAssertEqual(reading.weightOfProduct, 200)
    }

    // MARK: - Saturates rows that also contain a fat keyword

    func test_parse_readsGermanSaturatesRowAsSaturatesNotFat() {
        let reading = NutritionLabelParser.parse(lines: fatAndSaturatesLines(fatLabel: "Fett", saturatesLabel: "davon gesättigte Fettsäuren"))
        XCTAssertEqual(reading.fat, 12)
        XCTAssertEqual(reading.fatSaturated, 2)
    }

    func test_parse_readsEnglishSaturatedFatRowAsSaturatesNotFat() {
        let reading = NutritionLabelParser.parse(lines: fatAndSaturatesLines(fatLabel: "Fat", saturatesLabel: "Saturated fat"))
        XCTAssertEqual(reading.fat, 12)
        XCTAssertEqual(reading.fatSaturated, 2)
    }

    func test_parse_unsaturatedFatRowsNeverOverwriteFatOrSaturates() {
        let unsaturatedLabels = [
            "z toho mononenasycené mastné kyseliny",
            "of which mono-unsaturates",
            "davon einfach ungesättigte Fettsäuren",
            "kwasy tłuszczowe jednonienasycone"
        ]
        for label in unsaturatedLabels {
            var lines = fatAndSaturatesLines(fatLabel: "Fat", saturatesLabel: "Saturates")
            lines.append(RecognizedTextLine(text: label, boundingBox: CGRect(x: 0.1, y: 0.6, width: 0.4, height: 0.05)))
            lines.append(RecognizedTextLine(text: "5 g", boundingBox: CGRect(x: 0.6, y: 0.6, width: 0.15, height: 0.05)))
            let reading = NutritionLabelParser.parse(lines: lines)
            XCTAssertEqual(reading.fat, 12, label)
            XCTAssertEqual(reading.fatSaturated, 2, label)
        }
    }

    // MARK: - Package weight

    func test_parse_findsPackageWeightNextToAWeightKeyword() {
        var lines = czechLabelLines()
        lines.append(RecognizedTextLine(text: "Hmotnost: 250 g", boundingBox: CGRect(x: 0.1, y: 0.95, width: 0.3, height: 0.03)))
        let reading = NutritionLabelParser.parse(lines: lines)
        XCTAssertEqual(reading.weightOfProduct, 250)
    }

    func test_parse_convertsKilogramsToGrams() {
        var lines = czechLabelLines()
        lines.append(RecognizedTextLine(text: "Balení netto 1 kg", boundingBox: CGRect(x: 0.1, y: 0.95, width: 0.3, height: 0.03)))
        let reading = NutritionLabelParser.parse(lines: lines)
        XCTAssertEqual(reading.weightOfProduct, 1000)
    }

    // MARK: - Foundation Models merge

    func test_merging_fillsNameAndPortionsTheParserCouldNotFind() {
        let reading = NutritionLabelParser.parse(lines: czechLabelLines())
        let candidate = NutritionLabelModelCandidate(
            name: "Tvaroh",
            packageWeightGrams: nil,
            portions: [NutritionLabelPortionCandidate(name: "1 balení", grams: 250)]
        )
        let ocrText = (czechLabelLines().map(\.text) + ["Tvaroh", "1 balení 250 g"]).joined(separator: "\n")

        let merged = NutritionLabelParser.merging(reading, with: candidate, ocrText: ocrText)

        XCTAssertEqual(merged.name, "Tvaroh")
        XCTAssertEqual(merged.portions?.first?.grams, 250)
    }

    func test_merging_neverOverwritesAValueTheParserAlreadyFound() {
        let reading = NutritionLabelParser.parse(lines: czechLabelLines())
        let candidate = NutritionLabelModelCandidate(fatPer100g: 999)
        let ocrText = czechLabelLines().map(\.text).joined(separator: "\n") + "\n999"

        let merged = NutritionLabelParser.merging(reading, with: candidate, ocrText: ocrText)

        XCTAssertEqual(merged.fat, 12, "the parser's own deterministic value must win over the model's")
    }

    func test_merging_discardsAModelNumberThatDoesNotAppearInTheOCRText() {
        let reading = NutritionLabelReading()
        let candidate = NutritionLabelModelCandidate(packageWeightGrams: 250)
        let merged = NutritionLabelParser.merging(reading, with: candidate, ocrText: "no numbers here at all")

        XCTAssertNil(merged.weightOfProduct, "an ungrounded number must never be written into the form")
    }

    // MARK: - Fixtures

    private let saturatesValueBox = CGRect(x: 0.6, y: 0.65, width: 0.15, height: 0.05)
    private let carbohydrateValueBox = CGRect(x: 0.6, y: 0.55, width: 0.15, height: 0.05)
    private let energyValueBox = CGRect(x: 0.6, y: 0.8, width: 0.3, height: 0.05)

    private func czechLabelLines() -> [RecognizedTextLine] {
        [
            RecognizedTextLine(text: "Nutriční hodnoty na 100 g", boundingBox: CGRect(x: 0.6, y: 0.9, width: 0.3, height: 0.03)),
            RecognizedTextLine(text: "Energetická hodnota", boundingBox: CGRect(x: 0.1, y: 0.8, width: 0.3, height: 0.05)),
            RecognizedTextLine(text: "1550 kJ / 370 kcal", boundingBox: energyValueBox),
            RecognizedTextLine(text: "Tuky", boundingBox: CGRect(x: 0.1, y: 0.7, width: 0.3, height: 0.05)),
            RecognizedTextLine(text: "12 g", boundingBox: CGRect(x: 0.6, y: 0.7, width: 0.15, height: 0.05)),
            RecognizedTextLine(text: "z toho nasycené mastné kyseliny", boundingBox: CGRect(x: 0.1, y: 0.65, width: 0.4, height: 0.05)),
            RecognizedTextLine(text: "2 g", boundingBox: saturatesValueBox),
            RecognizedTextLine(text: "Sacharidy", boundingBox: CGRect(x: 0.1, y: 0.55, width: 0.3, height: 0.05)),
            RecognizedTextLine(text: "55 g", boundingBox: carbohydrateValueBox),
            RecognizedTextLine(text: "z toho cukry", boundingBox: CGRect(x: 0.1, y: 0.5, width: 0.3, height: 0.05)),
            RecognizedTextLine(text: "5 g", boundingBox: CGRect(x: 0.6, y: 0.5, width: 0.15, height: 0.05)),
            RecognizedTextLine(text: "Bílkoviny", boundingBox: CGRect(x: 0.1, y: 0.4, width: 0.3, height: 0.05)),
            RecognizedTextLine(text: "10 g", boundingBox: CGRect(x: 0.6, y: 0.4, width: 0.15, height: 0.05)),
            RecognizedTextLine(text: "Sůl", boundingBox: CGRect(x: 0.1, y: 0.3, width: 0.3, height: 0.05)),
            RecognizedTextLine(text: "1 g", boundingBox: CGRect(x: 0.6, y: 0.3, width: 0.15, height: 0.05))
        ]
    }

    private func fatAndSaturatesLines(fatLabel: String, saturatesLabel: String) -> [RecognizedTextLine] {
        [
            RecognizedTextLine(text: "Nutrition facts per 100 g", boundingBox: CGRect(x: 0.6, y: 0.9, width: 0.3, height: 0.03)),
            RecognizedTextLine(text: "Energy", boundingBox: CGRect(x: 0.1, y: 0.8, width: 0.3, height: 0.05)),
            RecognizedTextLine(text: "1550 kJ / 370 kcal", boundingBox: energyValueBox),
            RecognizedTextLine(text: fatLabel, boundingBox: CGRect(x: 0.1, y: 0.7, width: 0.3, height: 0.05)),
            RecognizedTextLine(text: "12 g", boundingBox: CGRect(x: 0.6, y: 0.7, width: 0.15, height: 0.05)),
            RecognizedTextLine(text: saturatesLabel, boundingBox: CGRect(x: 0.1, y: 0.65, width: 0.4, height: 0.05)),
            RecognizedTextLine(text: "2 g", boundingBox: saturatesValueBox)
        ]
    }

    private func multiLanguageLabelLines() -> [RecognizedTextLine] {
        [
            RecognizedTextLine(text: "100 g", boundingBox: CGRect(x: 0.6, y: 0.9, width: 0.15, height: 0.03)),
            RecognizedTextLine(text: "Energetická hodnota / Wartość energetyczna / Brennwert", boundingBox: CGRect(x: 0.1, y: 0.8, width: 0.4, height: 0.05)),
            RecognizedTextLine(text: "1550 kJ / 370 kcal", boundingBox: energyValueBox),
            RecognizedTextLine(text: "Tuky / Tłuszcz / Fett", boundingBox: CGRect(x: 0.1, y: 0.7, width: 0.4, height: 0.05)),
            RecognizedTextLine(text: "12 g", boundingBox: CGRect(x: 0.6, y: 0.7, width: 0.15, height: 0.05)),
            RecognizedTextLine(text: "Bílkoviny / Białko / Eiweiß", boundingBox: CGRect(x: 0.1, y: 0.4, width: 0.4, height: 0.05)),
            RecognizedTextLine(text: "10 g", boundingBox: CGRect(x: 0.6, y: 0.4, width: 0.15, height: 0.05))
        ]
    }
}
