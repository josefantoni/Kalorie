//
//  ApprovePhotoSuggestionUseCaseTests.swift
//  KalorieTests
//
//  Created by Josef Antoni on 07.11.2026.
//

import FirebaseStorage
import XCTest
@testable import Kalorie

final class ApprovePhotoSuggestionUseCaseTests: XCTestCase {

    // MARK: - Tests

    func test_approve_givesTheCatalogueItemAPhotoInCatalogPhotos_neverALinkIntoThePrivateSuggestionFolder() async throws {
        let (sut, dataProvider, storage) = makeSUT(currentItem: makePhotoSuggestionItem())
        let suggestion = try await makeStoredSuggestion(storage: storage)

        try await sut(suggestion, response: "Thanks")

        let call = try XCTUnwrap(dataProvider.sets.first { $0.collection == Constants.Firestore.foodItems })
        let dto = try XCTUnwrap(call.item as? FoodItemDTO)
        let photoURL = try XCTUnwrap(dto.photoUrl)
        XCTAssertTrue(photoURL.contains("catalogPhotos%2F12345678%2F"))
        XCTAssertFalse(photoURL.contains("photoSuggestions"), "the author keeps delete rights on the suggestion folder, so every user's photo must not live there")
    }

    func test_approve_whenTheCatalogueItemHasZeroCalories_stillAttachesThePhoto() async throws {
        let (sut, dataProvider, storage) = makeSUT(currentItem: makePhotoSuggestionItem(caloriesPerHundredGrams: 0))
        let suggestion = try await makeStoredSuggestion(storage: storage)

        try await sut(suggestion, response: "Thanks")

        let call = try XCTUnwrap(dataProvider.sets.first { $0.collection == Constants.Firestore.foodItems })
        let dto = try XCTUnwrap(call.item as? FoodItemDTO)
        XCTAssertNotNil(dto.photoUrl, "approving a photo must not re-validate an item the moderator did not edit")
        XCTAssertEqual(dto.caloriesPerHundredGrams, 0)
    }

    func test_approve_whenTheItemAlreadyHasAPhoto_replacesItAndDeletesTheOldFile() async throws {
        let existing = try XCTUnwrap(URL(string: "https://storage.fake/catalogPhotos%2F12345678%2Fold.jpg"))
        let (sut, dataProvider, storage) = makeSUT(currentItem: makePhotoSuggestionItem(photoURL: existing))
        storage.files[existing] = Data([9])
        let suggestion = try await makeStoredSuggestion(storage: storage)

        try await sut(suggestion, response: "Thanks")

        let call = try XCTUnwrap(dataProvider.sets.first { $0.collection == Constants.Firestore.foodItems })
        let dto = try XCTUnwrap(call.item as? FoodItemDTO)
        XCTAssertNotEqual(dto.photoUrl, existing.absoluteString, "a later suggestion that was pending before the item got its photo replaces it")
        XCTAssertTrue(storage.deletedURLs.contains(existing), "the replaced catalogue photo must not stay in Storage")
    }

    func test_approve_writesTheResolution_thenDeletesTheSuggestionFile_andLeavesTheDocument() async throws {
        let (sut, dataProvider, storage) = makeSUT(currentItem: makePhotoSuggestionItem())
        let suggestion = try await makeStoredSuggestion(storage: storage)

        try await sut(suggestion, response: "  Thanks for the photo!  ")

        let update = try XCTUnwrap(dataProvider.updates.first)
        XCTAssertEqual(update.collection, Constants.Firestore.photoSuggestions)
        XCTAssertEqual(update.id, suggestion.id)
        let json = try encodedJSON(try XCTUnwrap(update.item as? TicketResolutionDTO))
        XCTAssertEqual(json["status"] as? String, "accepted")
        XCTAssertEqual(json["response"] as? String, "Thanks for the photo!")
        XCTAssertEqual(json["hidden"] as? Bool, false)
        XCTAssertTrue(storage.deletedURLs.contains(suggestion.photoURL), "the file is deleted once the ticket records the outcome")
        XCTAssertTrue(dataProvider.deletes.isEmpty, "the ticket is kept so its author can read the response")
    }

    func test_approve_leavesOtherUsersPendingSuggestionsForTheItemAlone() async throws {
        let (sut, dataProvider, storage) = makeSUT(currentItem: makePhotoSuggestionItem())
        let suggestion = try await makeStoredSuggestion(storage: storage)
        dataProvider.stubbedDocumentIds = [Constants.Firestore.photoSuggestions: [suggestion.id, "other-user-ticket"]]

        try await sut(suggestion, response: "Thanks")

        XCTAssertEqual(dataProvider.updates.map(\.id), [suggestion.id], "approving one suggestion must not resolve or delete the others")
        XCTAssertTrue(dataProvider.deletes.isEmpty)
        XCTAssertEqual(storage.deletedURLs, [suggestion.photoURL])
    }

    func test_approve_withEmptyResponse_throwsAndChangesNothing() async throws {
        let (sut, dataProvider, storage) = makeSUT(currentItem: makePhotoSuggestionItem())
        let suggestion = try await makeStoredSuggestion(storage: storage)

        do {
            try await sut(suggestion, response: "  ")
            XCTFail("Expected responseRequired error")
        } catch TicketResolutionError.responseRequired {}

        XCTAssertTrue(dataProvider.sets.isEmpty, "the catalogue must not change when the ticket cannot be answered")
        XCTAssertTrue(dataProvider.updates.isEmpty)
        XCTAssertTrue(storage.deletedURLs.isEmpty)
    }

    func test_approve_whenTheItemIsGone_throwsItemMissing() async throws {
        let (sut, _, storage) = makeSUT(currentItem: nil)
        let suggestion = try await makeStoredSuggestion(storage: storage)

        do {
            try await sut(suggestion, response: "Thanks")
            XCTFail("Expected itemMissing error")
        } catch ApprovePhotoSuggestionError.itemMissing {}
    }

    func test_approve_whenTheCatalogueWriteFails_resolvesNothingAndKeepsTheFile() async throws {
        let (sut, dataProvider, storage) = makeSUT(currentItem: makePhotoSuggestionItem())
        let suggestion = try await makeStoredSuggestion(storage: storage)
        dataProvider.failingWriteCollections = [Constants.Firestore.foodItems]

        _ = try? await sut(suggestion, response: "Thanks")

        XCTAssertTrue(dataProvider.updates.isEmpty)
        XCTAssertFalse(storage.deletedURLs.contains(suggestion.photoURL), "the suggestion stays pending and can still be approved or rejected")
        XCTAssertFalse(storage.files.keys.contains { $0.absoluteString.contains("catalogPhotos") })
    }

    func test_approve_whenTheResolutionWriteFails_throwsAndKeepsTheFileForARetry() async throws {
        let (sut, dataProvider, storage) = makeSUT(currentItem: makePhotoSuggestionItem())
        let suggestion = try await makeStoredSuggestion(storage: storage)
        dataProvider.failingWriteCollections = [Constants.Firestore.photoSuggestions]

        do {
            try await sut(suggestion, response: "Thanks")
            XCTFail("the sheet must stay open for a retry")
        } catch {}

        XCTAssertFalse(storage.deletedURLs.contains(suggestion.photoURL), "a retry downloads the photo again")
    }

    func test_approve_whenTheFileDeleteFailsAfterTheResolution_doesNotThrow() async throws {
        let (sut, dataProvider, storage) = makeSUT(currentItem: makePhotoSuggestionItem())
        let suggestion = try await makeStoredSuggestion(storage: storage)
        storage.deleteError = URLError(.notConnectedToInternet)

        try await sut(suggestion, response: "Thanks")

        XCTAssertEqual(dataProvider.updates.map(\.id), [suggestion.id], "the ticket is resolved; an orphaned file is only logged")
    }

    func test_reject_writesTheResolution_thenDeletesTheFile_andLeavesTheDocument() async throws {
        let (_, dataProvider, storage) = makeSUT(currentItem: nil)
        let suggestion = try await makeStoredSuggestion(storage: storage)
        let sut = makeRejectSUT(dataProvider: dataProvider, storage: storage)

        try await sut(suggestion, response: "Out of focus")

        let json = try encodedJSON(try XCTUnwrap(dataProvider.updates.first?.item as? TicketResolutionDTO))
        XCTAssertEqual(json["status"] as? String, "rejected")
        XCTAssertEqual(json["response"] as? String, "Out of focus")
        XCTAssertEqual(json["hidden"] as? Bool, false)
        XCTAssertEqual(storage.deletedURLs, [suggestion.photoURL])
        XCTAssertTrue(dataProvider.deletes.isEmpty)
    }

    func test_reject_withEmptyResponse_throwsAndChangesNothing() async throws {
        let (_, dataProvider, storage) = makeSUT(currentItem: nil)
        let suggestion = try await makeStoredSuggestion(storage: storage)
        let sut = makeRejectSUT(dataProvider: dataProvider, storage: storage)

        do {
            try await sut(suggestion, response: "")
            XCTFail("Expected responseRequired error")
        } catch TicketResolutionError.responseRequired {}

        XCTAssertTrue(dataProvider.updates.isEmpty)
        XCTAssertTrue(storage.deletedURLs.isEmpty)
    }

    func test_reject_whenTheFileIsAlreadyGone_stillResolves() async throws {
        let (_, dataProvider, storage) = makeSUT(currentItem: nil)
        let suggestion = try await makeStoredSuggestion(storage: storage)
        storage.deleteError = NSError(domain: StorageErrorDomain, code: StorageErrorCode.objectNotFound.rawValue)
        let sut = makeRejectSUT(dataProvider: dataProvider, storage: storage)

        try await sut(suggestion, response: "Out of focus")

        XCTAssertEqual(dataProvider.updates.map(\.id), [suggestion.id])
    }

    func test_reject_whenTheResolutionWriteFails_keepsTheFile() async throws {
        let (_, dataProvider, storage) = makeSUT(currentItem: nil)
        let suggestion = try await makeStoredSuggestion(storage: storage)
        dataProvider.failingWriteCollections = [Constants.Firestore.photoSuggestions]
        let sut = makeRejectSUT(dataProvider: dataProvider, storage: storage)

        do {
            try await sut(suggestion, response: "Out of focus")
            XCTFail("the sheet must stay open for a retry")
        } catch {}

        XCTAssertTrue(storage.deletedURLs.isEmpty, "deleting the file of a still pending ticket would leave an unanswerable row")
    }

    // MARK: - Helpers

    private func makeSUT(currentItem: FoodItemDomain?) -> (sut: ApprovePhotoSuggestionUseCase, dataProvider: TicketDataProviderFake, storage: StorageDataProviderFake) {
        let dataProvider = TicketDataProviderFake()
        dataProvider.currentItem = currentItem
        let storage = StorageDataProviderFake()
        let authProvider = AuthProviderFake(userId: "maintainer")
        let sut = ApprovePhotoSuggestionUseCase(
            dataProvider: dataProvider,
            authProvider: authProvider,
            storageProvider: storage,
            fetchFoodItemByBarcode: FetchFoodItemByBarcodeUseCaseFake(stubbedItem: currentItem.map { FoodItemDTO(item: $0).asDomain() }),
            downloadFoodPhoto: DownloadFoodPhotoUseCase(storageProvider: storage),
            updateFoodItem: UpdateFoodItemUseCase(
                dataProvider: dataProvider,
                authProvider: authProvider,
                uploadFoodPhoto: UploadFoodPhotoUseCase(storageProvider: storage),
                deleteFoodPhoto: DeleteFoodPhotoUseCase(storageProvider: storage)
            )
        )
        return (sut, dataProvider, storage)
    }

    private func makeRejectSUT(dataProvider: TicketDataProviderFake, storage: StorageDataProviderFake) -> RejectPhotoSuggestionUseCase {
        RejectPhotoSuggestionUseCase(
            dataProvider: dataProvider,
            authProvider: AuthProviderFake(userId: "maintainer"),
            storageProvider: storage
        )
    }

    private func makeStoredSuggestion(storage: StorageDataProviderFake) async throws -> PhotoSuggestionDomain {
        let url = try await storage.uploadAsync(
            data: Data([7]),
            path: "photoSuggestions/alice/12345678/ticket-1.jpg",
            contentType: "image/jpeg"
        )
        return PhotoSuggestionDomain(
            id: "ticket-1",
            itemId: "12345678",
            suggestedBy: "alice",
            photoURL: url,
            suggestedAt: .now,
            status: .pending,
            locale: "en"
        )
    }

    private func encodedJSON(_ dto: some Encodable) throws -> [String: Any] {
        let data = try JSONEncoder().encode(dto)
        return try XCTUnwrap(JSONSerialization.jsonObject(with: data) as? [String: Any])
    }
}
