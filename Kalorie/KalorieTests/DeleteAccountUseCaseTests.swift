//
//  DeleteAccountUseCaseTests.swift
//  KalorieTests
//
//  Created by Josef Antoni on 07.08.2026.
//

import XCTest
import FirebaseAuth
@testable import Kalorie

final class DeleteAccountUseCaseTests: XCTestCase {

    // MARK: - Tests

    func test_callAsFunction_deletesFirestoreDataBeforeDeletingAuthAccount() async throws {
        let log = OperationLog()
        let dataProvider = DeleteAccountDataProviderFake(log: log)
        dataProvider.stubbedMealTypes = [MealTypeDTO(id: 0, name: "Snídaně", startMinutes: 0, endMinutes: 60)]
        dataProvider.stubbedFoodConsumed = [makeFood(id: "f1")]
        let sut = makeSUT(dataProvider: dataProvider, log: log)

        try await sut()

        XCTAssertEqual(log.entries.last, "deleteCurrentUser")
        XCTAssertTrue(log.entries.dropLast().contains("deleteMealType:0"))
        XCTAssertTrue(log.entries.dropLast().contains("deleteFood:f1"))
    }

    func test_callAsFunction_deletesAllMealTypesAndFoodsById() async throws {
        let log = OperationLog()
        let dataProvider = DeleteAccountDataProviderFake(log: log)
        dataProvider.stubbedMealTypes = [
            MealTypeDTO(id: 0, name: "Snídaně", startMinutes: 0, endMinutes: 60),
            MealTypeDTO(id: 1, name: "Oběd", startMinutes: 60, endMinutes: 120)
        ]
        dataProvider.stubbedFoodConsumed = [makeFood(id: "f1"), makeFood(id: "f2")]
        let sut = makeSUT(dataProvider: dataProvider, log: log)

        try await sut()

        XCTAssertTrue(Set(["0", "1", "f1", "f2"]).isSubset(of: Set(dataProvider.deletedIds)))
    }

    func test_callAsFunction_whenNotAuthenticated_throwsAuthError() async {
        let log = OperationLog()
        let sut = DeleteAccountUseCase(
            dataProvider: DeleteAccountDataProviderFake(log: log),
            authProvider: AuthProviderFake(userId: nil),
            authCommandProvider: DeleteAccountAuthCommandProviderFake(log: log)
        )

        do {
            _ = try await sut()
            XCTFail("Expected AuthError to be thrown")
        } catch is AuthError {
            // pass
        } catch {
            XCTFail("Expected AuthError but got \(error)")
        }
    }

    func test_callAsFunction_whenRequiresRecentLogin_throwsTypedError() async {
        let log = OperationLog()
        let authCommandProvider = DeleteAccountAuthCommandProviderFake(log: log)
        authCommandProvider.deleteError = NSError(domain: AuthErrorDomain, code: AuthErrorCode.requiresRecentLogin.rawValue)
        let sut = DeleteAccountUseCase(
            dataProvider: DeleteAccountDataProviderFake(log: log),
            authProvider: AuthProviderFake(),
            authCommandProvider: authCommandProvider
        )

        do {
            try await sut()
            XCTFail("Expected DeleteAccountError.requiresRecentLogin to be thrown")
        } catch DeleteAccountError.requiresRecentLogin {
            // pass
        } catch {
            XCTFail("Expected requiresRecentLogin but got \(error)")
        }
    }

    func test_callAsFunction_whenDeleteFailsWithOtherError_propagatesError() async {
        let log = OperationLog()
        let authCommandProvider = DeleteAccountAuthCommandProviderFake(log: log)
        authCommandProvider.deleteError = URLError(.notConnectedToInternet)
        let sut = DeleteAccountUseCase(
            dataProvider: DeleteAccountDataProviderFake(log: log),
            authProvider: AuthProviderFake(),
            authCommandProvider: authCommandProvider
        )

        do {
            try await sut()
            XCTFail("Expected error to be thrown")
        } catch DeleteAccountError.requiresRecentLogin {
            XCTFail("Did not expect requiresRecentLogin")
        } catch {
            // pass
        }
    }

    // MARK: - Helpers

    private func makeSUT(dataProvider: DeleteAccountDataProviderFake, log: OperationLog) -> DeleteAccountUseCase {
        DeleteAccountUseCase(
            dataProvider: dataProvider,
            authProvider: AuthProviderFake(),
            authCommandProvider: DeleteAccountAuthCommandProviderFake(log: log)
        )
    }

    private func makeFood(id: String) -> FoodConsumedDTO {
        FoodConsumedDTO(
            id: id,
            czName: "Test",
            engName: "Test",
            weight: 100,
            date: Date().timeIntervalSince1970,
            calories: 100,
            protein: 1,
            carbohydrate: 1,
            carbohydrateSugar: 1,
            fat: 1,
            fatUnsaturated: 1,
            fiber: 1,
            salt: 1
        )
    }
}

private final class OperationLog {
    private(set) var entries: [String] = []
    func record(_ entry: String) { entries.append(entry) }
}

private final class DeleteAccountDataProviderFake: FirestoreDataProviderProtocol {

    // MARK: - Properties

    private let log: OperationLog
    var stubbedMealTypes: [MealTypeDTO] = []
    var stubbedFoodConsumed: [FoodConsumedDTO] = []
    private(set) var deletedIds: [String] = []

    // MARK: - Init

    init(log: OperationLog) {
        self.log = log
    }

    // MARK: - Functions

    func loadAsync<T: Decodable>(from collection: String) async throws -> [T] {
        if collection.hasSuffix("mealTypes") {
            return stubbedMealTypes.compactMap { $0 as? T }
        }
        return stubbedFoodConsumed.compactMap { $0 as? T }
    }

    func loadFromServerAsync<T: Decodable>(from collection: String) async throws -> [T] { [] }
    func loadAsync<T: Decodable>(from collection: String, where field: String, isGreaterThanOrEqualTo lowerBound: Double, isLessThan upperBound: Double) async throws -> [T] { [] }
    func loadAsync<T: Decodable>(from collection: String, where field: String, hasPrefix prefix: String, limit: Int) async throws -> [T] { [] }
    func loadAsync<T: Decodable>(from collection: String, where field: String, isEqualTo value: String) async throws -> T? { nil }
    func saveAsync<T: Encodable>(_ item: T, to collection: String) async throws {}
    func setAsync<T: Encodable>(_ item: T, id: String, in collection: String) async throws {}
    func batchSetAsync<T: Encodable>(_ items: [(item: T, id: String)], in collection: String) async throws {}

    func deleteAsync(id: String, from collection: String) async throws {
        deletedIds.append(id)
        if collection.hasSuffix("mealTypes") {
            log.record("deleteMealType:\(id)")
        } else if collection.hasSuffix("foodConsumed") {
            log.record("deleteFood:\(id)")
        } else {
            log.record("deleteUser:\(id)")
        }
    }
}

private final class DeleteAccountAuthCommandProviderFake: AuthCommandProviderProtocol {

    // MARK: - Properties

    private let log: OperationLog
    var deleteError: Error?

    // MARK: - Init

    init(log: OperationLog) {
        self.log = log
    }

    // MARK: - Functions

    func link(with credential: AuthCredential) async throws {}
    func signIn(with credential: AuthCredential) async throws {}
    func signOut() throws {}
    func updateDisplayName(_ name: String) async throws {}

    func deleteCurrentUser() async throws {
        log.record("deleteCurrentUser")
        if let deleteError { throw deleteError }
    }
}
