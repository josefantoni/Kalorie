//
//  SubmitFoodItemUseCaseTests.swift
//  KalorieTests
//
//  Created by Josef Antoni on 10.09.2026.
//

import XCTest
@testable import Kalorie

final class SubmitFoodItemUseCaseTests: XCTestCase {

    // MARK: - Tests

    func test_submit_whenNotAuthenticated_throwsAuthError() async throws {
        let (sut, _, _) = makeSUT(userId: nil)
        do {
            _ = try await sut(makeItem(), photo: localPhoto)
            XCTFail("Expected notAuthenticated error")
        } catch AuthError.notAuthenticated {
            // pass
        }
    }

    func test_submit_withInvalidItem_throwsValidationErrorAndDoesNotWrite() async throws {
        let (sut, dataProvider, _) = makeSUT()
        do {
            _ = try await sut(makeItem(id: "123"), photo: localPhoto)
            XCTFail("Expected invalidCode error")
        } catch FoodItemSubmissionError.invalidCode {
            // pass
        }
        XCTAssertFalse(dataProvider.didWriteSubmission)
    }

    func test_submit_whenBarcodeAlreadyInCatalogue_throwsItemAlreadyExistsAndDoesNotWrite() async throws {
        let (sut, dataProvider, _) = makeSUT()
        let item = makeItem()
        dataProvider.stubbedExistingFoodItem = FoodItemDTO(item: item)
        do {
            _ = try await sut(item, photo: localPhoto)
            XCTFail("Expected itemAlreadyExists error")
        } catch FoodItemSubmissionError.itemAlreadyExists {
            // pass
        }
        XCTAssertFalse(dataProvider.didWriteSubmission)
    }

    func test_submit_withValidItem_writesPendingSubmissionToFoodItemSubmissions() async throws {
        let (sut, dataProvider, _) = makeSUT(userId: "user-123")
        let item = makeItem()
        let result = try await sut(item, photo: localPhoto)
        XCTAssertEqual(result.barcode, item.id, "the item already has a real barcode; it must be kept, not discarded")
        XCTAssertEqual(result.submittedBy, "user-123")
        XCTAssertEqual(result.status, .pending)
        XCTAssertNil(result.rejectReason)
        XCTAssertTrue(dataProvider.didWriteSubmission)
        XCTAssertEqual(dataProvider.writtenCollection, Constants.Firestore.foodItemSubmissions)
        XCTAssertEqual(dataProvider.writtenId, result.id)
    }

    func test_submit_withEmptyId_usesTheGeneratedSubmissionIdAsTheItemsIdentityAndHasNoBarcode() async throws {
        let (sut, _, _) = makeSUT(userId: "user-123")
        let item = makeItem(id: "", name: "Kukuřice")
        let result = try await sut(item, photo: localPhoto)
        XCTAssertEqual(result.item.id, result.id, "a barcode-less item's identity is the submission's own id, so favourites/portions/entries keep pointing at the right document once approved")
        XCTAssertNil(result.barcode, "the id is a UUID, not a barcode, and must never be sent to OpenFoodFacts or shown as one")
    }

    func test_submit_withoutPhoto_throwsPhotoMissingAndUploadsAndWritesNothing() async throws {
        let (sut, dataProvider, storage) = makeSUT()
        do {
            _ = try await sut(makeItem(), photo: .none)
            XCTFail("Expected photoMissing error")
        } catch FoodItemSubmissionError.photoMissing {
            // pass
        }
        XCTAssertTrue(storage.uploadedPaths.isEmpty)
        XCTAssertFalse(dataProvider.didWriteSubmission)
    }

    func test_submit_withLocalPhoto_uploadsToTheAuthorsFolderAndStoresItsUrlOnTheItem() async throws {
        let (sut, _, storage) = makeSUT(userId: "user-123")
        let result = try await sut(makeItem(), photo: localPhoto)
        let path = try XCTUnwrap(storage.uploadedPaths.first)
        XCTAssertTrue(path.hasPrefix("submissionPhotos/user-123/"), "storage rules only let the author write into their own folder")
        let uploaded = try XCTUnwrap(storage.files.keys.first)
        XCTAssertEqual(result.item.photoURL, uploaded)
    }

    func test_submit_withRemotePhoto_keepsItAndUploadsNothing() async throws {
        let (sut, _, storage) = makeSUT()
        let url = try XCTUnwrap(URL(string: "https://storage.fake/existing.jpg"))
        let result = try await sut(makeItem(), photo: .remote(url))
        XCTAssertEqual(result.item.photoURL, url)
        XCTAssertTrue(storage.uploadedPaths.isEmpty)
    }

    func test_submit_whenTheWriteFails_deletesTheUploadedPhoto() async throws {
        let (sut, dataProvider, storage) = makeSUT()
        dataProvider.writeError = URLError(.notConnectedToInternet)
        do {
            _ = try await sut(makeItem(), photo: localPhoto)
            XCTFail("Expected the write error")
        } catch {
            XCTAssertEqual((error as? URLError)?.code, .notConnectedToInternet)
        }
        XCTAssertTrue(storage.files.isEmpty, "a failed submission must not leave a file nobody points at")
    }

    func test_submit_whenTheUploadFails_throwsPhotoUploadFailedAndWritesNothing() async throws {
        let (sut, dataProvider, storage) = makeSUT()
        storage.uploadError = URLError(.notConnectedToInternet)
        do {
            _ = try await sut(makeItem(), photo: localPhoto)
            XCTFail("Expected photoUploadFailed error")
        } catch FoodItemSubmissionError.photoUploadFailed {
            // pass
        }
        XCTAssertFalse(dataProvider.didWriteSubmission)
    }

    func test_submit_whenTheBarcodeAlreadyExists_uploadsNothing() async throws {
        let (sut, dataProvider, storage) = makeSUT()
        let item = makeItem()
        dataProvider.stubbedExistingFoodItem = FoodItemDTO(item: item)
        _ = try? await sut(item, photo: localPhoto)
        XCTAssertTrue(storage.uploadedPaths.isEmpty)
    }

    // MARK: - Helpers

    private let localPhoto = FoodItemFormPhoto.local(Data([1, 2, 3]))

    private func makeSUT(userId: String? = "test-user") -> (sut: SubmitFoodItemUseCase, dataProvider: SubmitFoodItemDataProviderFake, storage: StorageDataProviderFake) {
        let dataProvider = SubmitFoodItemDataProviderFake()
        let storage = StorageDataProviderFake()
        let sut = SubmitFoodItemUseCase(
            dataProvider: dataProvider,
            authProvider: AuthProviderFake(userId: userId),
            uploadFoodPhoto: UploadFoodPhotoUseCase(storageProvider: storage),
            deleteFoodPhoto: DeleteFoodPhotoUseCase(storageProvider: storage)
        )
        return (sut, dataProvider, storage)
    }

    private func makeItem(id: String = "12345678", name: String = "Tvaroh") -> FoodItemDomain {
        FoodItemDomain(
            id: id,
            kind: .catalogue,
            czName: name,
            engName: "Cottage cheese",
            weight: 200,
            date: .now,
            energyKJ: 335,
            caloriesPerHundredGrams: 80,
            fat: 0.5,
            fatSaturated: 0.3,
            fatUnsaturatedFattyAcids: 0.2,
            carbohydrate: 4,
            carbohydratePureSugar: 3,
            fiber: 0,
            protein: 13,
            salt: 0.1
        )
    }
}

private final class SubmitFoodItemDataProviderFake: FirestoreDataProviderProtocol {

    // MARK: - Properties

    var stubbedExistingFoodItem: FoodItemDTO?
    var didWriteSubmission = false
    var writtenCollection: String?
    var writtenId: String?
    var writeError: Error?

    // MARK: - Functions

    func loadAsync<T: Decodable>(from collection: String) async throws -> [T] { [] }
    func loadFromServerAsync<T: Decodable>(from collection: String) async throws -> [T] { [] }
    func loadAsync<T: Decodable>(from collection: String, where field: String, isGreaterThanOrEqualTo lowerBound: Double, isLessThan upperBound: Double) async throws -> [T] { [] }
    func loadAsync<T: Decodable>(from collection: String, where field: String, hasPrefix prefix: String, limit: Int) async throws -> [T] { [] }
    func loadAsync<T: Decodable>(from collection: String, where field: String, arrayContains value: String, limit: Int) async throws -> [T] { [] }
    func loadAsync<T: Decodable>(from collection: String, where field: String, isEqualTo value: String) async throws -> T? { nil }
    func loadAsync<T: Decodable>(from collection: String, where field: String, isEqualTo value: String, orderBy orderField: String, descending: Bool) async throws -> [T] { [] }
    func loadAsync<T: Decodable>(id: String, from collection: String) async throws -> T? { nil }
    func loadFromServerAsync<T: Decodable>(id: String, from collection: String) async throws -> T? { stubbedExistingFoodItem as? T }
    func loadAsync<T: Decodable>(from collection: String, orderBy field: String, descending: Bool, limit: Int) async throws -> [T] { [] }
    func saveAsync<T: Encodable>(_ item: T, to collection: String) async throws {}
    func setAsync<T: Encodable>(_ item: T, id: String, in collection: String) async throws {
        if let writeError { throw writeError }
        didWriteSubmission = true
        writtenCollection = collection
        writtenId = id
    }
    func batchSetAsync<T: Encodable>(_ items: [(item: T, id: String)], in collection: String) async throws {}
    func deleteAsync(id: String, from collection: String) async throws {}
}
