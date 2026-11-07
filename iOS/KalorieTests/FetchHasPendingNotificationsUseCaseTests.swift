//
//  FetchHasPendingNotificationsUseCaseTests.swift
//  KalorieTests
//
//  Created by Josef Antoni on 07.11.2026.
//

import XCTest
@testable import Kalorie

final class FetchHasPendingNotificationsUseCaseTests: XCTestCase {

    // MARK: - Tests

    func test_whenNothingWaits_returnsFalse() async {
        let (sut, _) = makeSUT()
        let hasPending = await sut()
        XCTAssertFalse(hasPending)
    }

    func test_whenAnySingleQueueIsNonEmpty_returnsTrue() async {
        for collection in [Constants.Firestore.foodItemSubmissions, Constants.Firestore.foodItemReports, Constants.Firestore.photoSuggestions] {
            let (sut, dataProvider) = makeSUT()
            dataProvider.stubbedCounts[collection] = 1
            let hasPending = await sut()
            XCTAssertTrue(hasPending, "\(collection) alone must light the dot")
        }
    }

    func test_whenACountFails_returnsFalseInsteadOfShowingAnError() async {
        let (sut, dataProvider) = makeSUT()
        dataProvider.stubbedCounts[Constants.Firestore.photoSuggestions] = 3
        dataProvider.countError = URLError(.notConnectedToInternet)
        let hasPending = await sut()
        XCTAssertFalse(hasPending, "a stale maintainer claim or a network loss must not surface as an alert on the Dashboard")
    }

    // MARK: - Helpers

    private func makeSUT() -> (sut: FetchHasPendingNotificationsUseCase, dataProvider: PhotoSuggestionDataProviderFake) {
        let dataProvider = PhotoSuggestionDataProviderFake()
        return (FetchHasPendingNotificationsUseCase(dataProvider: dataProvider), dataProvider)
    }
}
