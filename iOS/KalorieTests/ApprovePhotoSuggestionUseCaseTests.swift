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
        let item = makePhotoSuggestionItem()
        let (sut, dataProvider, storage) = makeSUT(currentItem: item)
        let suggestion = try await makeSuggestion(storage: storage)

        try await sut(suggestion)

        let call = try XCTUnwrap(dataProvider.setCalls.first { $0.collection == Constants.Firestore.foodItems })
        let dto = try XCTUnwrap(call.item as? FoodItemDTO)
        let photoURL = try XCTUnwrap(dto.photoUrl)
        XCTAssertTrue(photoURL.contains("catalogPhotos%2F12345678%2F"))
        XCTAssertFalse(photoURL.contains("photoSuggestions"), "the author keeps delete rights on the suggestion folder, so every user's photo must not live there")
    }

    func test_approve_whenTheCatalogueItemHasZeroCalories_stillAttachesThePhoto() async throws {
        let item = makePhotoSuggestionItem(caloriesPerHundredGrams: 0)
        let (sut, dataProvider, storage) = makeSUT(currentItem: item)
        let suggestion = try await makeSuggestion(storage: storage)

        try await sut(suggestion)

        let call = try XCTUnwrap(dataProvider.setCalls.first { $0.collection == Constants.Firestore.foodItems })
        let dto = try XCTUnwrap(call.item as? FoodItemDTO)
        XCTAssertNotNil(dto.photoUrl, "approving a photo must not re-validate an item the moderator did not edit")
        XCTAssertEqual(dto.caloriesPerHundredGrams, 0)
    }

    func test_approve_deletesEveryUsersSuggestionForTheItem_filesAndDocuments() async throws {
        let item = makePhotoSuggestionItem()
        let (sut, dataProvider, storage) = makeSUT(currentItem: item)
        let suggestion = try await makeSuggestion(storage: storage)
        dataProvider.stubbedSuggestionDocumentIds = ["12345678_alice", "12345678_bob"]

        try await sut(suggestion)

        let deleted = dataProvider.deletedIds.filter { $0.collection == Constants.Firestore.photoSuggestions }.map(\.id)
        XCTAssertEqual(Set(deleted), ["12345678_alice", "12345678_bob"], "suggestions of other users would otherwise wait for an item that already has a photo")
        XCTAssertEqual(
            Set(storage.deletedPaths),
            ["photoSuggestions/alice/12345678.jpg", "photoSuggestions/bob/12345678.jpg"]
        )
    }

    func test_approve_whenTheItemAlreadyHasAPhoto_throwsAndOverwritesNothing() async throws {
        let existing = try XCTUnwrap(URL(string: "https://example.com/catalogPhotos/12345678/x.jpg"))
        let item = makePhotoSuggestionItem(photoURL: existing)
        let (sut, dataProvider, storage) = makeSUT(currentItem: item)
        let suggestion = try await makeSuggestion(storage: storage)

        do {
            try await sut(suggestion)
            XCTFail("Expected itemAlreadyHasPhoto error")
        } catch ApprovePhotoSuggestionError.itemAlreadyHasPhoto {
            // pass
        }

        XCTAssertTrue(dataProvider.setCalls.isEmpty)
        XCTAssertTrue(dataProvider.deletedIds.isEmpty, "the suggestion stays so the maintainer can still reject it")
    }

    func test_approve_whenTheItemIsGone_throwsItemMissing() async throws {
        let (sut, _, storage) = makeSUT(currentItem: nil)
        let suggestion = try await makeSuggestion(storage: storage)

        do {
            try await sut(suggestion)
            XCTFail("Expected itemMissing error")
        } catch ApprovePhotoSuggestionError.itemMissing {
            // pass
        }
    }

    func test_approve_whenTheCatalogueWriteFails_keepsTheSuggestionsAndDoesNotLeaveACatalogueFile() async throws {
        let item = makePhotoSuggestionItem()
        let (sut, dataProvider, storage) = makeSUT(currentItem: item)
        let suggestion = try await makeSuggestion(storage: storage)
        dataProvider.stubbedSetError = URLError(.notConnectedToInternet)

        _ = try? await sut(suggestion)

        XCTAssertTrue(dataProvider.deletedIds.isEmpty)
        XCTAssertFalse(storage.files.keys.contains { $0.absoluteString.contains("catalogPhotos") })
    }

    func test_reject_deletesTheFileAndTheDocumentOfThatSuggestionOnly() async throws {
        let (_, dataProvider, storage) = makeSUT(currentItem: nil)
        let suggestion = try await makeSuggestion(storage: storage)
        let sut = RejectPhotoSuggestionUseCase(
            dataProvider: dataProvider,
            authProvider: AuthProviderFake(userId: "maintainer"),
            storageProvider: storage
        )

        try await sut(suggestion)

        XCTAssertEqual(storage.deletedPaths, ["photoSuggestions/alice/12345678.jpg"])
        XCTAssertEqual(dataProvider.deletedIds.map(\.id), ["12345678_alice"])
    }

    func test_reject_whenTheFileIsAlreadyGone_stillDeletesTheDocument() async throws {
        let (_, dataProvider, storage) = makeSUT(currentItem: nil)
        let suggestion = try await makeSuggestion(storage: storage)
        storage.deleteError = NSError(domain: StorageErrorDomain, code: StorageErrorCode.objectNotFound.rawValue)
        let sut = RejectPhotoSuggestionUseCase(
            dataProvider: dataProvider,
            authProvider: AuthProviderFake(userId: "maintainer"),
            storageProvider: storage
        )

        try await sut(suggestion)

        XCTAssertEqual(dataProvider.deletedIds.map(\.id), ["12345678_alice"], "a missing file must not keep the row in the queue forever")
    }

    func test_reject_whenTheFileDeleteFailsForAnotherReason_keepsTheDocumentSoItCanBeRetried() async throws {
        let (_, dataProvider, storage) = makeSUT(currentItem: nil)
        let suggestion = try await makeSuggestion(storage: storage)
        storage.deleteError = URLError(.notConnectedToInternet)
        let sut = RejectPhotoSuggestionUseCase(
            dataProvider: dataProvider,
            authProvider: AuthProviderFake(userId: "maintainer"),
            storageProvider: storage
        )

        do {
            try await sut(suggestion)
            XCTFail("the failure must reach the caller")
        } catch {
            XCTAssertEqual((error as? URLError)?.code, .notConnectedToInternet)
        }

        XCTAssertTrue(dataProvider.deletedIds.isEmpty, "deleting the document would orphan the file with nothing left to retry from")
    }

    // MARK: - Helpers

    private func makeSUT(currentItem: FoodItemDomain?) -> (sut: ApprovePhotoSuggestionUseCase, dataProvider: PhotoSuggestionDataProviderFake, storage: StorageDataProviderFake) {
        let dataProvider = PhotoSuggestionDataProviderFake()
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

    private func makeSuggestion(storage: StorageDataProviderFake) async throws -> PhotoSuggestionDomain {
        let url = try await storage.uploadAsync(
            data: Data([7]),
            path: "photoSuggestions/alice/12345678.jpg",
            contentType: "image/jpeg"
        )
        return PhotoSuggestionDomain(itemId: "12345678", suggestedBy: "alice", photoURL: url, suggestedAt: .now)
    }
}
