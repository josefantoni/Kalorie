//
//  MigrateAnonymousDataUseCaseTests.swift
//  KalorieTests
//
//  Created by Josef Antoni on 07.08.2026.
//

import XCTest
import FirebaseAuth
@testable import Kalorie

final class MigrateAnonymousDataUseCaseTests: XCTestCase {

    // MARK: - migrate

    func test_migrate_carriesFavouriteFoodsThroughWithSameLifecycleAsFoodConsumed() async throws {
        let (sut, dataProvider, _, _) = makeSUT()
        dataProvider.stubbedFavouriteFoods = [makeFavourite(id: "fav1")]

        try await sut.migrate(fromAnonymousUserId: "anon-1", credential: makeCredential())

        XCTAssertEqual(dataProvider.batchSavedFavouriteFoodIds, ["fav1"], "an anonymous user's favourites must survive sign-in exactly like their logged food")
    }

    func test_migrate_carriesMyCreatedMealsThroughWithSameLifecycleAsFoodConsumed() async throws {
        let (sut, dataProvider, _, _) = makeSUT()
        dataProvider.stubbedMyCreatedMeals = [makeMeal(id: "meal1")]

        try await sut.migrate(fromAnonymousUserId: "anon-1", credential: makeCredential())

        XCTAssertEqual(dataProvider.batchSavedMyCreatedMealIds, ["meal1"], "an anonymous user's created meals must survive sign-in exactly like their logged food")
    }

    func test_migrate_signsInWithCredentialAfterSavingSnapshot() async throws {
        let (sut, dataProvider, authCommandProvider, snapshotStore) = makeSUT()
        dataProvider.stubbedFoodConsumed = [makeFood(id: "f1")]

        try await sut.migrate(fromAnonymousUserId: "anon-1", credential: makeCredential())

        XCTAssertEqual(authCommandProvider.signInCallCount, 1)
        XCTAssertNil(snapshotStore.stubbedSnapshot, "snapshot musí být po dokončení smazán")
    }

    func test_migrate_withEmptySource_writesNoItemsAndCleansUp() async throws {
        let (sut, dataProvider, _, snapshotStore) = makeSUT()
        dataProvider.stubbedFoodConsumed = []

        try await sut.migrate(fromAnonymousUserId: "anon-1", credential: makeCredential())

        XCTAssertEqual(dataProvider.batchSavedItemCount, 0)
        XCTAssertEqual(snapshotStore.deleteCallCount, 1)
    }

    func test_migrate_runTwice_isIdempotent() async throws {
        let (sut, dataProvider, _, _) = makeSUT()
        dataProvider.stubbedFoodConsumed = [makeFood(id: "f1"), makeFood(id: "f2")]

        try await sut.migrate(fromAnonymousUserId: "anon-1", credential: makeCredential())
        try await sut.migrate(fromAnonymousUserId: "anon-1", credential: makeCredential())

        XCTAssertEqual(dataProvider.batchSavedItemCount, 2, "opakovaný setAsync se stejným id přepíše, nevytvoří duplicitu")
    }

    // MARK: - resumeIfNeeded

    func test_resumeIfNeeded_withNoSnapshot_doesNothing() async throws {
        let (sut, dataProvider, authCommandProvider, _) = makeSUT()

        try await sut.resumeIfNeeded()

        XCTAssertEqual(dataProvider.batchSavedItemCount, 0)
        XCTAssertEqual(authCommandProvider.signInCallCount, 0)
    }

    func test_resumeIfNeeded_whenStillOnSourceUid_discardsSnapshotWithoutWriting() async throws {
        let (sut, dataProvider, _, snapshotStore) = makeSUT(currentUserId: "anon-1")
        snapshotStore.stubbedSnapshot = PendingMergeSnapshot(sourceAnonymousUserId: "anon-1", foodConsumed: [makeFood(id: "f1")], favouriteFoods: [], myCreatedMeals: [])

        try await sut.resumeIfNeeded()

        XCTAssertEqual(dataProvider.batchSavedItemCount, 0)
        XCTAssertEqual(snapshotStore.deleteCallCount, 1)
    }

    func test_resumeIfNeeded_whenAlreadyOnTargetUid_finishesWriteAndCleansUp() async throws {
        let (sut, dataProvider, _, snapshotStore) = makeSUT(currentUserId: "target-1")
        snapshotStore.stubbedSnapshot = PendingMergeSnapshot(sourceAnonymousUserId: "anon-1", foodConsumed: [makeFood(id: "f1")], favouriteFoods: [], myCreatedMeals: [])

        try await sut.resumeIfNeeded()

        XCTAssertEqual(dataProvider.batchSavedItemCount, 1)
        XCTAssertEqual(snapshotStore.deleteCallCount, 1)
    }

    // MARK: - Helpers

    private func makeSUT(
        currentUserId: String? = "target-1"
    ) -> (
        sut: MigrateAnonymousDataUseCase,
        dataProvider: MigrateAnonymousDataProviderFake,
        authCommandProvider: AuthCommandProviderFake,
        snapshotStore: PendingMergeSnapshotStoreFake
    ) {
        let dataProvider = MigrateAnonymousDataProviderFake()
        let authCommandProvider = AuthCommandProviderFake()
        let snapshotStore = PendingMergeSnapshotStoreFake()
        let sut = MigrateAnonymousDataUseCase(
            dataProvider: dataProvider,
            authProvider: AuthProviderFake(userId: currentUserId),
            authCommandProvider: authCommandProvider,
            snapshotStore: snapshotStore
        )
        return (sut, dataProvider, authCommandProvider, snapshotStore)
    }

    private func makeCredential() -> AuthCredential {
        EmailAuthProvider.credential(withEmail: "test@example.com", password: "password")
    }

    private func makeFood(id: String) -> FoodConsumedDTO {
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
            salt: 1
        )
    }

    private func makeFavourite(id: String) -> FavouriteFoodDTO {
        FavouriteFoodDTO(
            item: FoodItemDomain(
                id: id,
                kind: .catalogue,
                czName: "Test",
                engName: "Test",
                weight: 100,
                date: .now,
                energyKJ: 400,
                caloriesPerHundredGrams: 100,
                fat: 1,
                fatSaturated: 1,
                fatUnsaturatedFattyAcids: 1,
                carbohydrate: 1,
                carbohydratePureSugar: 1,
                fiber: 1,
                protein: 1,
                salt: 1
            ),
            favouritedAt: .now
        )
    }

    private func makeMeal(id: String) -> MyCreatedMealDTO {
        MyCreatedMealDTO(
            meal: MyCreatedMealDomain(
                id: id,
                name: "Test",
                ingredients: [
                    MyCreatedMealIngredientDomain(
                        foodItemId: "12345",
                        czName: "Test",
                        engName: "Test",
                        grams: 50,
                        nutrition: FoodNutritionValues(
                            energyKJ: 400,
                            caloriesPerHundredGrams: 100,
                            fat: 1,
                            fatSaturated: 1,
                            fatUnsaturatedFattyAcids: 1,
                            carbohydrate: 1,
                            carbohydratePureSugar: 1,
                            fiber: 1,
                            protein: 1,
                            salt: 1
                        )
                    )
                ],
                createdAt: .now,
                updatedAt: .now
            )
        )
    }
}

private final class MigrateAnonymousDataProviderFake: FirestoreDataProviderProtocol {

    // MARK: - Properties

    var stubbedFoodConsumed: [FoodConsumedDTO] = []
    var stubbedFavouriteFoods: [FavouriteFoodDTO] = []
    var stubbedMyCreatedMeals: [MyCreatedMealDTO] = []
    private(set) var batchSavedItemCount = 0
    private(set) var batchSavedFavouriteFoodIds: [String] = []
    private(set) var batchSavedMyCreatedMealIds: [String] = []

    // MARK: - Functions

    func loadAsync<T: Decodable>(from collection: String) async throws -> [T] {
        if collection.contains("favouriteFoods") {
            return stubbedFavouriteFoods.compactMap { $0 as? T }
        }
        if collection.contains("myCreatedMeals") {
            return stubbedMyCreatedMeals.compactMap { $0 as? T }
        }
        return stubbedFoodConsumed.compactMap { $0 as? T }
    }

    func loadFromServerAsync<T: Decodable>(from collection: String) async throws -> [T] { [] }
    func loadAsync<T: Decodable>(from collection: String, where field: String, isGreaterThanOrEqualTo lowerBound: Double, isLessThan upperBound: Double) async throws -> [T] { [] }
    func loadAsync<T: Decodable>(from collection: String, where field: String, hasPrefix prefix: String, limit: Int) async throws -> [T] { [] }
    func loadAsync<T: Decodable>(from collection: String, where field: String, isEqualTo value: String) async throws -> T? { nil }
    func loadAsync<T: Decodable>(from collection: String, orderBy field: String, descending: Bool, limit: Int) async throws -> [T] { [] }
    func saveAsync<T: Encodable>(_ item: T, to collection: String) async throws {}
    func setAsync<T: Encodable>(_ item: T, id: String, in collection: String) async throws {}

    func batchSetAsync<T: Encodable>(_ items: [(item: T, id: String)], in collection: String) async throws {
        if collection.contains("favouriteFoods") {
            batchSavedFavouriteFoodIds = items.map(\.id)
        } else if collection.contains("myCreatedMeals") {
            batchSavedMyCreatedMealIds = items.map(\.id)
        } else {
            batchSavedItemCount = items.count
        }
    }

    func deleteAsync(id: String, from collection: String) async throws {}
}
