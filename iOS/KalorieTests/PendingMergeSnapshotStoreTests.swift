//
//  PendingMergeSnapshotStoreTests.swift
//  KalorieTests
//
//  Created by Josef Antoni on 07.08.2026.
//

import XCTest
@testable import Kalorie

final class PendingMergeSnapshotStoreTests: XCTestCase {

    // MARK: - Lifecycle

    override func tearDownWithError() throws {
        try PendingMergeSnapshotStore().delete()
        try super.tearDownWithError()
    }

    // MARK: - Tests

    func test_load_withNoSavedSnapshot_returnsNil() throws {
        let sut = makeSUT()
        XCTAssertNil(try sut.load())
    }

    func test_saveThenLoad_returnsEqualSnapshot() throws {
        let sut = makeSUT()
        let snapshot = makeSnapshot(sourceUserId: "anon-1", foodIds: ["f1", "f2"])

        try sut.save(snapshot)
        let loaded = try sut.load()

        XCTAssertEqual(loaded?.sourceAnonymousUserId, "anon-1")
        XCTAssertEqual(loaded?.foodConsumed.map(\.id), ["f1", "f2"])
    }

    func test_delete_removesSnapshot() throws {
        let sut = makeSUT()
        try sut.save(makeSnapshot(sourceUserId: "anon-1", foodIds: ["f1"]))

        try sut.delete()

        XCTAssertNil(try sut.load())
    }

    func test_delete_whenNoSnapshotExists_doesNotThrow() {
        let sut = makeSUT()
        XCTAssertNoThrow(try sut.delete())
    }

    func test_save_overwritesPreviousSnapshot() throws {
        let sut = makeSUT()
        try sut.save(makeSnapshot(sourceUserId: "first", foodIds: ["f1"]))

        try sut.save(makeSnapshot(sourceUserId: "second", foodIds: ["f2"]))

        XCTAssertEqual(try sut.load()?.sourceAnonymousUserId, "second")
    }

    func test_load_snapshotWrittenBeforeBodyWeights_stillDecodes_soAPendingMergeCanFinish() throws {
        let legacy = """
        {"sourceAnonymousUserId":"anon-1","foodConsumed":[],"favouriteFoods":[],"myCreatedMeals":[],"foodItemPortions":[]}
        """
        let decoded = try JSONDecoder().decode(PendingMergeSnapshot.self, from: Data(legacy.utf8))
        XCTAssertEqual(decoded.sourceAnonymousUserId, "anon-1")
        XCTAssertTrue(decoded.bodyWeights.isEmpty)
        XCTAssertNil(decoded.appSettings)
    }

    func test_saveAndLoad_roundTripsBodyWeightsAndSettings() throws {
        let sut = makeSUT()
        addTeardownBlock { try? sut.delete() }
        let snapshot = PendingMergeSnapshot(
            sourceAnonymousUserId: "anon-1",
            foodConsumed: [],
            favouriteFoods: [],
            myCreatedMeals: [],
            bodyWeights: [BodyWeightDTO(weight: BodyWeightDomain(dayKey: "2026-10-27", kilograms: 78.44))],
            appSettings: AppSettingsDTO(settings: AppSettingsDomain(showBodyWeight: false, bodyWeightUnit: .lb))
        )
        try sut.save(snapshot)
        let loaded = try sut.load()
        XCTAssertEqual(loaded?.bodyWeights.map(\.weightKg), [78.44])
        XCTAssertEqual(loaded?.appSettings?.asDomain().bodyWeightUnit, .lb)
    }

    // MARK: - Helpers

    private func makeSUT() -> PendingMergeSnapshotStore {
        PendingMergeSnapshotStore()
    }

    private func makeSnapshot(sourceUserId: String, foodIds: [String]) -> PendingMergeSnapshot {
        PendingMergeSnapshot(
            sourceAnonymousUserId: sourceUserId,
            foodConsumed: foodIds.map { id in
                FoodConsumedDTO(
                    id: id,
                    foodItemId: id,
                    foodItemKind: .catalogue,
                    czName: "Test",
                    engName: "Test",
                    weight: 100,
                    date: Date().timeIntervalSince1970,
                    calories: 100,
                    caloriesPerHundredGrams: nil,
                    energyKJ: nil,
                    protein: 1,
                    carbohydrate: 1,
                    carbohydrateSugar: 1,
                    fat: 1,
                    fatSaturated: nil,
                    fatUnsaturated: 1,
                    fiber: 1,
                    salt: 1,
                    mealTypeId: nil,
                    measureUnit: nil,
                    alcoholByVolume: nil
                )
            },
            favouriteFoods: [],
            myCreatedMeals: []
        )
    }
}
