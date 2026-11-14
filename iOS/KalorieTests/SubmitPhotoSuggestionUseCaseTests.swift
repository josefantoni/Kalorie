//
//  SubmitPhotoSuggestionUseCaseTests.swift
//  KalorieTests
//
//  Created by Josef Antoni on 07.11.2026.
//

import XCTest
@testable import Kalorie

final class SubmitPhotoSuggestionUseCaseTests: XCTestCase {

    // MARK: - Tests

    func test_submitPhotoSuggestion_whenNotAuthenticated_throwsAndTouchesNothing() async throws {
        let (sut, dataProvider, storage) = makeSUT(userId: nil)
        do {
            try await sut(itemId: "12345678", photo: Data([1]))
            XCTFail("Expected notAuthenticated error")
        } catch AuthError.notAuthenticated {}
        XCTAssertTrue(storage.uploadedPaths.isEmpty)
        XCTAssertTrue(dataProvider.sets.isEmpty)
    }

    func test_submitPhotoSuggestion_uploadsToAPathWithTheItemAndTheSuggestionId() async throws {
        let (sut, dataProvider, storage) = makeSUT(userId: "user-42")
        dataProvider.newIds = ["suggestion-id"]
        try await sut(itemId: "12345678", photo: Data([1]))
        XCTAssertEqual(
            storage.uploadedPaths,
            ["photoSuggestions/user-42/12345678/suggestion-id.jpg"],
            "the Storage rule reads the item from the path and the Firestore rule matches the suggestion id inside photo_url"
        )
        XCTAssertEqual(dataProvider.sets.map(\.id), ["suggestion-id"])
        let json = try encodedJSON(try XCTUnwrap(dataProvider.sets.first?.item as? PhotoSuggestionCreateDTO))
        let photoURL = try XCTUnwrap(json["photo_url"] as? String)
        XCTAssertTrue(photoURL.contains("%2Fuser-42%2F12345678%2Fsuggestion-id.jpg"))
    }

    func test_submitPhotoSuggestion_writesAPendingVisibleTicketWithTheAppLocaleAndNoMaintainerKeys() async throws {
        let (sut, dataProvider, _) = makeSUT(locale: "cs")
        try await sut(itemId: "12345678", photo: Data([1]))
        let json = try encodedJSON(try XCTUnwrap(dataProvider.sets.first?.item as? PhotoSuggestionCreateDTO))
        XCTAssertEqual(
            Set(json.keys),
            ["item_id", "suggested_by", "suggested_at", "photo_url", "status", "locale", "hidden"]
        )
        XCTAssertEqual(json["status"] as? String, "pending")
        XCTAssertEqual(json["hidden"] as? Bool, false)
        XCTAssertEqual(json["locale"] as? String, "cs")
    }

    func test_submitPhotoSuggestion_twiceForTheSameItem_usesTwoDifferentFiles() async throws {
        let (sut, _, storage) = makeSUT()
        try await sut(itemId: "12345678", photo: Data([1]))
        try await sut(itemId: "12345678", photo: Data([2]))
        XCTAssertEqual(Set(storage.uploadedPaths).count, 2, "a resolved ticket no longer blocks a new suggestion for the item")
    }

    func test_submitPhotoSuggestion_afterASuccessfulWrite_marksThatTheUserMayHaveUnread() async throws {
        let store = UserNotificationsStoreFake()
        let (sut, _, _) = makeSUT(store: store)
        try await sut(itemId: "12345678", photo: Data([1]))
        XCTAssertTrue(store.mayHaveUnread)
        XCTAssertNotNil(store.lastSubmittedAt)
    }

    func test_submitPhotoSuggestion_whenTheUploadFails_writesNothingAndDoesNotMarkMayHaveUnread() async throws {
        let store = UserNotificationsStoreFake()
        let (sut, dataProvider, storage) = makeSUT(store: store)
        storage.uploadError = URLError(.notConnectedToInternet)
        _ = try? await sut(itemId: "12345678", photo: Data([1]))
        XCTAssertTrue(dataProvider.sets.isEmpty)
        XCTAssertFalse(store.mayHaveUnread)
    }

    func test_submitPhotoSuggestion_whenTheDocumentWriteFails_deletesTheUploadedFileAndDoesNotMarkMayHaveUnread() async throws {
        let store = UserNotificationsStoreFake()
        let (sut, dataProvider, storage) = makeSUT(userId: "user-42", store: store)
        dataProvider.newIds = ["suggestion-id"]
        dataProvider.failingWriteCollections = [Constants.Firestore.photoSuggestions]
        do {
            try await sut(itemId: "12345678", photo: Data([1]))
            XCTFail("the failure must reach the caller")
        } catch {
            XCTAssertEqual((error as? URLError)?.code, .notConnectedToInternet)
        }
        XCTAssertEqual(storage.deletedPaths, ["photoSuggestions/user-42/12345678/suggestion-id.jpg"], "an orphaned file would be unreachable for the user and invisible to the maintainer")
        XCTAssertFalse(store.mayHaveUnread)
    }

    // MARK: - Helpers

    private func makeSUT(
        userId: String? = "test-user",
        store: UserNotificationsStoreFake = UserNotificationsStoreFake(),
        locale: String = "en"
    ) -> (sut: SubmitPhotoSuggestionUseCase, dataProvider: TicketDataProviderFake, storage: StorageDataProviderFake) {
        let dataProvider = TicketDataProviderFake()
        let storage = StorageDataProviderFake()
        let sut = SubmitPhotoSuggestionUseCase(
            dataProvider: dataProvider,
            authProvider: AuthProviderFake(userId: userId),
            storageProvider: storage,
            notificationsStore: store,
            locale: locale
        )
        return (sut, dataProvider, storage)
    }

    private func encodedJSON(_ dto: some Encodable) throws -> [String: Any] {
        let data = try JSONEncoder().encode(dto)
        return try XCTUnwrap(JSONSerialization.jsonObject(with: data) as? [String: Any])
    }
}
