//
//  FoodItemFormInputTests.swift
//  KalorieTests
//
//  Created by Josef Antoni on 12.09.2026.
//

import XCTest
@testable import Kalorie

final class FoodItemFormInputTests: XCTestCase {

    func test_initFromItem_preservesEnglishName() {
        let item = makeItem(engName: "Cottage cheese")
        let sut = FoodItemFormInput(item: item)
        XCTAssertEqual(sut.engName, "Cottage cheese")
    }

    func test_asFoodItemDomain_onSlovakDevice_storesTheTypedNameAsSlovakOnly() {
        var sut = FoodItemFormInput()
        sut.nameLanguage = "sk"
        sut.name = "Mlieko"

        let item = sut.asFoodItemDomain(id: "1")

        XCTAssertEqual(item.skName, "Mlieko")
        XCTAssertEqual(item.czName, "", "a Slovak name typed on a Slovak device must not be stored as Czech")
        XCTAssertEqual(item.engName, "")
    }

    func test_asFoodItemDomain_onEnglishDevice_storesTheTypedNameAsEnglishOnly() {
        var sut = FoodItemFormInput()
        sut.nameLanguage = "en"
        sut.name = "Milk"

        let item = sut.asFoodItemDomain(id: "1")

        XCTAssertEqual(item.engName, "Milk")
        XCTAssertEqual(item.czName, "")
        XCTAssertEqual(item.skName, "")
    }

    func test_asFoodItemDomain_trimsTheNames() {
        var sut = FoodItemFormInput()
        sut.nameLanguage = "cs"
        sut.name = "  Mléko  "

        XCTAssertEqual(sut.asFoodItemDomain(id: "1").czName, "Mléko")
    }

    func test_initFromSubmission_editsTheOriginalLanguageNameWhateverTheDeviceLanguage() {
        let submission = FoodItemSubmissionDomain(
            id: "sub-1",
            barcode: nil,
            submittedBy: "user-1",
            status: .pending,
            submittedAt: .now,
            rejectReason: nil,
            nameLanguage: "sk",
            item: makeItem(czName: "", skName: "Mlieko", engName: "")
        )
        var sut = FoodItemFormInput(submission: submission)

        XCTAssertEqual(sut.name, "Mlieko")
        sut.name = "Mlieko polotučné"

        let item = sut.asFoodItemDomain()
        XCTAssertEqual(item.skName, "Mlieko polotučné")
        XCTAssertEqual(item.czName, "", "editing a submission must not move its name into another language")
    }

    func test_initFromItem_preservesSlovakName() {
        XCTAssertEqual(FoodItemFormInput(item: makeItem(skName: "Tvaroh mäkký")).skName, "Tvaroh mäkký")
    }

    func test_initFromItem_withNilFatSaturatedAndFiber_keepsThemNil() {
        let item = makeItem(fatSaturated: nil, fiber: nil)
        let sut = FoodItemFormInput(item: item)
        XCTAssertNil(sut.fatSaturated)
        XCTAssertNil(sut.fiber)
    }

    func test_initFromItem_withKnownFatSaturatedAndFiber_keepsTheirValues() {
        let item = makeItem(fatSaturated: 1.5, fiber: 2.5)
        let sut = FoodItemFormInput(item: item)
        XCTAssertEqual(sut.fatSaturated, 1.5)
        XCTAssertEqual(sut.fiber, 2.5)
    }

    func test_asFoodItemDomain_roundTripsEnglishName() {
        var sut = FoodItemFormInput(item: makeItem(engName: "Cottage cheese"))
        sut.czName = "Tvaroh opraveno"
        XCTAssertEqual(sut.asFoodItemDomain().engName, "Cottage cheese", "editing an unrelated field must not blank the English name")
    }

    func test_asFoodItemDomain_whenFatSaturatedAndFiberWereUnknownAndUntouched_staysNil() {
        var sut = FoodItemFormInput(item: makeItem(fatSaturated: nil, fiber: nil))
        sut.czName = "Tvaroh opraveno"
        let result = sut.asFoodItemDomain()
        XCTAssertNil(result.fatSaturated, "unknown nutrition must not silently become a false zero")
        XCTAssertNil(result.fiber, "unknown nutrition must not silently become a false zero")
    }

    func test_asFoodItemDomain_whenFatSaturatedIsExplicitlyEdited_usesTheEditedValue() {
        var sut = FoodItemFormInput(item: makeItem(fatSaturated: nil))
        sut.fatSaturated = 3
        XCTAssertEqual(sut.asFoodItemDomain().fatSaturated, 3)
    }

    func test_asFoodItemDomain_forNewItem_writesHundredAsPackageWeightBecauseTheUserNoLongerEntersIt() {
        var sut = FoodItemFormInput()
        sut.measure = .millilitres
        let result = sut.asFoodItemDomain()
        XCTAssertEqual(result.weight, 100)
        XCTAssertEqual(result.measure, .millilitres)
    }

    func test_initFromItem_keepsStoredPackageWeightUntouchedAcrossARoundTrip() {
        let sut = FoodItemFormInput(item: makeItem(weight: 1500))
        XCTAssertEqual(sut.weightOfProduct, 1500)
        XCTAssertEqual(sut.asFoodItemDomain().weight, 1500)
    }

    func test_applying_readingWithMillilitres_setsMeasureAndReportsItChanged() {
        var sut = FoodItemFormInput()
        let applied = sut.applying(NutritionLabelReading(measure: .millilitres))
        XCTAssertEqual(sut.measure, .millilitres)
        XCTAssertTrue(applied.contains(.measure))
    }

    func test_applying_whenFormAlreadyAtMillilitres_isNotOverwrittenByGrams() {
        var sut = FoodItemFormInput()
        sut.measure = .millilitres
        _ = sut.applying(NutritionLabelReading(measure: .grams))
        XCTAssertEqual(sut.measure, .millilitres, "an explicitly picked measure must not be overwritten by a later reading, same as every other field")
    }

    func test_initFromItem_preservesAlcoholByVolume() {
        let item = makeItem(alcoholByVolume: 4.4)
        let sut = FoodItemFormInput(item: item)
        XCTAssertEqual(sut.alcoholByVolume, 4.4)
    }

    func test_asFoodItemDomain_withAlcoholByVolumeEntered_writesTheValue() {
        var sut = FoodItemFormInput()
        sut.alcoholByVolume = 4.4
        XCTAssertEqual(sut.asFoodItemDomain().alcoholByVolume, 4.4)
    }

    func test_asFoodItemDomain_withZeroAlcoholByVolume_writesNil() {
        var sut = FoodItemFormInput()
        sut.alcoholByVolume = 0
        XCTAssertNil(sut.asFoodItemDomain().alcoholByVolume, "zero means the maintainer left the field untouched, same as an empty input")
    }

    func test_asFoodItemDomain_withNoAlcoholByVolume_writesNil() {
        let sut = FoodItemFormInput()
        XCTAssertNil(sut.asFoodItemDomain().alcoholByVolume)
    }

    // MARK: - Helpers

    private func makeItem(
        czName: String = "Tvaroh",
        skName: String = "Tvaroh",
        engName: String = "Cottage cheese",
        fatSaturated: Double? = 0.3,
        fiber: Double? = 0,
        weight: Double = 200,
        alcoholByVolume: Double? = nil
    ) -> FoodItemDomain {
        FoodItemDomain(
            id: "12345678",
            kind: .catalogue,
            czName: czName,
            skName: skName,
            engName: engName,
            weight: weight,
            date: .now,
            energyKJ: 335,
            caloriesPerHundredGrams: 80,
            fat: 0.5,
            fatSaturated: fatSaturated,
            fatUnsaturatedFattyAcids: 0.2,
            carbohydrate: 4,
            carbohydratePureSugar: 3,
            fiber: fiber,
            protein: 13,
            salt: 0.1,
            alcoholByVolume: alcoholByVolume
        )
    }
}
