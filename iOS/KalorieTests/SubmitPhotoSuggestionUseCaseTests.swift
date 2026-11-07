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

    func test_submit_whenNotAuthenticated_throwsAuthError() async {
        let (sut, _, _) = makeSUT(userId: nil)
        do {
            try await sut(itemId: "12345678", photo: Data([1]))
            XCTFail("Expected notAuthenticated error")
        } catch AuthError.notAuthenticated {
            // pass
        } catch {
            XCTFail("Unexpected error \(error)")
        }
    }

    func test_submit_clearsAStaleOwnFileBeforeUploading_soARetryIsNotBlockedByTheRulesDenyingOverwrite() async throws {
        let (sut, _, storage) = makeSUT()
        let path = "photoSuggestions/alice/12345678.jpg"
        let staleURL = try await storage.uploadAsync(data: Data([9]), path: path, contentType: "image/jpeg")

        try await sut(itemId: "12345678", photo: Data([1]))

        XCTAssertEqual(storage.deletedPaths.first, path, "the fixed path must be cleared before the new upload")
        XCTAssertEqual(storage.files[staleURL], Data([1]), "the new photo replaces the stale one")
    }

    func test_submit_whenASuggestionIsAlreadyPending_touchesNeitherTheStoredFileNorTheDocument() async throws {
        let (sut, dataProvider, storage) = makeSUT()
        let path = "photoSuggestions/alice/12345678.jpg"
        let pendingURL = try await storage.uploadAsync(data: Data([9]), path: path, contentType: "image/jpeg")
        dataProvider.stubbedExistingSuggestions["12345678_alice"] = PhotoSuggestionDTO(
            itemId: "12345678",
            suggestedBy: "alice",
            photoURL: pendingURL,
            suggestedAt: .now
        )
        let uploadsBefore = storage.uploadedPaths

        try await sut(itemId: "12345678", photo: Data([1]))

        XCTAssertTrue(storage.deletedPaths.isEmpty, "the rules deny update, so deleting the pending file would leave a document with a dead photo_url")
        XCTAssertEqual(storage.uploadedPaths, uploadsBefore)
        XCTAssertEqual(storage.files[pendingURL], Data([9]))
        XCTAssertTrue(dataProvider.setCalls.isEmpty)
    }

    func test_submit_whenTheExistingSuggestionCannotBeRead_throwsAndTouchesNoFile() async throws {
        let (sut, dataProvider, storage) = makeSUT()
        let path = "photoSuggestions/alice/12345678.jpg"
        _ = try await storage.uploadAsync(data: Data([9]), path: path, contentType: "image/jpeg")
        dataProvider.stubbedLoadError = URLError(.notConnectedToInternet)

        do {
            try await sut(itemId: "12345678", photo: Data([1]))
            XCTFail("Expected the read error")
        } catch let error as URLError {
            XCTAssertEqual(error.code, .notConnectedToInternet)
        }

        XCTAssertTrue(storage.deletedPaths.isEmpty)
    }

    func test_submit_whenTheStaleFileCannotBeDeleted_stillUploads() async throws {
        let (sut, _, storage) = makeSUT()
        storage.deleteError = URLError(.fileDoesNotExist)

        try await sut(itemId: "12345678", photo: Data([1]))

        XCTAssertEqual(storage.uploadedPaths, ["photoSuggestions/alice/12345678.jpg"])
    }

    func test_submit_writesAPhotoURLThatPointsAtTheSuggestionFolderUnderTheItemAndUserId() async throws {
        let (sut, dataProvider, _) = makeSUT()

        try await sut(itemId: "12345678", photo: Data([1]))

        let call = try XCTUnwrap(dataProvider.setCalls.first)
        XCTAssertEqual(call.collection, Constants.Firestore.photoSuggestions)
        XCTAssertEqual(call.id, "12345678_alice")
        let dto = try XCTUnwrap(call.item as? PhotoSuggestionDTO)
        XCTAssertEqual(dto.suggestedBy, "alice")
        XCTAssertEqual(dto.itemId, "12345678")
        XCTAssertTrue(dto.photoUrl.contains("photoSuggestions%2Falice%2F12345678.jpg"), "the rules only accept the author's own suggestion file for that item")
    }

    func test_submit_whenTheFirestoreWriteFails_deletesTheUploadedFileAndRethrows() async {
        let (sut, dataProvider, storage) = makeSUT()
        dataProvider.stubbedSetError = URLError(.notConnectedToInternet)

        do {
            try await sut(itemId: "12345678", photo: Data([1]))
            XCTFail("Expected the write error")
        } catch let error as URLError {
            XCTAssertEqual(error.code, .notConnectedToInternet)
        } catch {
            XCTFail("Unexpected error \(error)")
        }

        XCTAssertTrue(storage.files.isEmpty, "an uploaded file without a document is invisible to the maintainer and can never be reviewed")
    }

    func test_submit_whenTheWriteFailsButAConcurrentAttemptCreatedTheDocument_keepsTheFileThatDocumentPointsTo() async throws {
        let (sut, dataProvider, storage) = makeSUT()
        dataProvider.stubbedSetError = URLError(.networkConnectionLost)
        let url = try XCTUnwrap(URL(string: "https://example.com/photoSuggestions/alice/12345678.jpg"))
        dataProvider.suggestionsAppearingAfterFailedSet["12345678_alice"] = PhotoSuggestionDTO(
            itemId: "12345678",
            suggestedBy: "alice",
            photoURL: url,
            suggestedAt: .now
        )

        do {
            try await sut(itemId: "12345678", photo: Data([1]))
            XCTFail("Expected the write error")
        } catch {
            XCTAssertEqual((error as? URLError)?.code, .networkConnectionLost)
        }

        XCTAssertEqual(storage.files.count, 1, "deleting the file would leave the concurrent attempt's document pointing at nothing")
    }

    func test_submit_whenTheUploadFails_writesNoDocument() async {
        let (sut, dataProvider, storage) = makeSUT()
        storage.uploadError = URLError(.timedOut)

        _ = try? await sut(itemId: "12345678", photo: Data([1]))

        XCTAssertTrue(dataProvider.setCalls.isEmpty)
    }

    // MARK: - Helpers

    private func makeSUT(userId: String? = "alice") -> (sut: SubmitPhotoSuggestionUseCase, dataProvider: PhotoSuggestionDataProviderFake, storage: StorageDataProviderFake) {
        let dataProvider = PhotoSuggestionDataProviderFake()
        let storage = StorageDataProviderFake()
        let sut = SubmitPhotoSuggestionUseCase(
            dataProvider: dataProvider,
            authProvider: AuthProviderFake(userId: userId),
            storageProvider: storage
        )
        return (sut, dataProvider, storage)
    }
}
