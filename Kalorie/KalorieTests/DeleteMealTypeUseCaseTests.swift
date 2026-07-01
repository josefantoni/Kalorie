//
//  DeleteMealTypeUseCaseTests.swift
//  KalorieTests
//
//  Created by Josef Antoni on 30.06.2026.
//

import XCTest
@testable import Kalorie

final class DeleteMealTypeUseCaseTests: XCTestCase {

    // MARK: - Tests

    func test_deleteMealType_callsDeleteWithCorrectId() async throws {
        let (sut, dataProvider) = makeSUT()
        let mealType = MealTypeDomain(id: 42, name: "Oběd", startTime: .now, endTime: .now)

        try await sut(mealType)

        XCTAssertEqual(dataProvider.deletedId, "42")
    }

    func test_deleteMealType_withoutAuth_throws() async {
        let dataProvider = FirestoreDataProviderStub()
        let sut = DeleteMealTypeUseCase(dataProvider: dataProvider, authProvider: AuthProviderFake(userId: nil))
        let mealType = MealTypeDomain(id: 1, name: "Test", startTime: .now, endTime: .now)

        do {
            try await sut(mealType)
            XCTFail("Expected AuthError.notAuthenticated")
        } catch AuthError.notAuthenticated {
            // pass
        } catch {
            XCTFail("Unexpected error: \(error)")
        }
    }

    // MARK: - Helpers

    private func makeSUT() -> (sut: DeleteMealTypeUseCase, dataProvider: FirestoreDataProviderStub) {
        let dataProvider = FirestoreDataProviderStub()
        let sut = DeleteMealTypeUseCase(dataProvider: dataProvider, authProvider: AuthProviderFake())
        return (sut, dataProvider)
    }
}
