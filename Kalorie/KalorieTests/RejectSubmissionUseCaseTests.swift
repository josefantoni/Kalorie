//
//  RejectSubmissionUseCaseTests.swift
//  KalorieTests
//
//  Created by Josef Antoni on 10.09.2026.
//

import FirebaseFirestore
import XCTest
@testable import Kalorie

final class RejectSubmissionUseCaseTests: XCTestCase {

    // MARK: - Tests

    func test_reject_whenNotAuthenticated_throwsAuthError() async throws {
        let (sut, _) = makeSUT(userId: nil)
        do {
            try await sut(makeSubmission(), reason: "Wrong calories")
            XCTFail("Expected notAuthenticated error")
        } catch AuthError.notAuthenticated {
            // pass
        }
    }

    func test_reject_withEmptyReason_throwsReasonRequiredAndDoesNotWrite() async throws {
        let (sut, dataProvider) = makeSUT()
        do {
            try await sut(makeSubmission(), reason: "   ")
            XCTFail("Expected reasonRequired error")
        } catch RejectSubmissionError.reasonRequired {
            // pass
        }
        XCTAssertNil(dataProvider.writtenId)
    }

    func test_reject_withReason_writesRejectedStatusPreservingOtherFields() async throws {
        let (sut, dataProvider) = makeSUT()
        let submission = makeSubmission()
        try await sut(submission, reason: "Wrong calories")
        XCTAssertEqual(dataProvider.writtenCollection, Constants.Firestore.foodItemSubmissions)
        XCTAssertEqual(dataProvider.writtenId, submission.id)
        XCTAssertEqual(dataProvider.writtenDTO?.status, .rejected)
        XCTAssertEqual(dataProvider.writtenDTO?.rejectReason, "Wrong calories")
        XCTAssertEqual(dataProvider.writtenDTO?.barcode, submission.barcode)
        XCTAssertEqual(dataProvider.writtenDTO?.submittedBy, submission.submittedBy)
    }

    func test_reject_whenWriteDeniedAndSubmissionNoLongerExists_throwsAlreadyResolved() async throws {
        let (sut, dataProvider) = makeSUT()
        dataProvider.stubbedSetError = NSError(domain: FirestoreErrorDomain, code: FirestoreErrorCode.permissionDenied.rawValue)
        dataProvider.stubbedReReadDTO = nil
        do {
            try await sut(makeSubmission(), reason: "Wrong calories")
            XCTFail("Expected alreadyResolved error")
        } catch RejectSubmissionError.alreadyResolved {
            // pass
        }
    }

    func test_reject_whenWriteDeniedButSubmissionStillQueued_rethrowsOriginalError() async throws {
        let (sut, dataProvider) = makeSUT()
        let deniedError = NSError(domain: FirestoreErrorDomain, code: FirestoreErrorCode.permissionDenied.rawValue)
        dataProvider.stubbedSetError = deniedError
        dataProvider.stubbedReReadDTO = FoodItemSubmissionDTO(
            id: "sub-1",
            barcode: "12345678",
            submittedBy: "some-user",
            status: .pending,
            submittedAt: .now,
            rejectReason: nil,
            item: makeSubmission().item
        )
        do {
            try await sut(makeSubmission(), reason: "Wrong calories")
            XCTFail("Expected the original permissionDenied error")
        } catch RejectSubmissionError.alreadyResolved {
            XCTFail("Should not report alreadyResolved while the submission is still queued")
        } catch {
            XCTAssertEqual(error as NSError, deniedError)
        }
    }

    func test_reject_whenWriteDeniedAndReReadFails_rethrowsOriginalWriteError() async throws {
        let (sut, dataProvider) = makeSUT()
        let deniedError = NSError(domain: FirestoreErrorDomain, code: FirestoreErrorCode.permissionDenied.rawValue)
        dataProvider.stubbedSetError = deniedError
        dataProvider.stubbedReReadError = NSError(domain: FirestoreErrorDomain, code: FirestoreErrorCode.permissionDenied.rawValue, userInfo: [NSLocalizedDescriptionKey: "expired session"])
        do {
            try await sut(makeSubmission(), reason: "Wrong calories")
            XCTFail("Expected the original write error")
        } catch RejectSubmissionError.alreadyResolved {
            XCTFail("Should not report alreadyResolved when the re-read itself fails")
        } catch {
            XCTAssertEqual(error as NSError, deniedError)
        }
    }

    // MARK: - Helpers

    private func makeSUT(userId: String? = "maintainer-user") -> (sut: RejectSubmissionUseCase, dataProvider: RejectSubmissionDataProviderFake) {
        let dataProvider = RejectSubmissionDataProviderFake()
        let authProvider = AuthProviderFake(userId: userId)
        let sut = RejectSubmissionUseCase(dataProvider: dataProvider, authProvider: authProvider)
        return (sut, dataProvider)
    }

    private func makeSubmission(id: String = "sub-1", barcode: String = "12345678") -> FoodItemSubmissionDomain {
        FoodItemSubmissionDomain(
            id: id,
            barcode: barcode,
            submittedBy: "some-user",
            status: .pending,
            submittedAt: .now,
            rejectReason: nil,
            item: FoodItemDomain(
                id: barcode,
                kind: .catalogue,
                czName: "Tvaroh",
                engName: "",
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
        )
    }
}

private final class RejectSubmissionDataProviderFake: FirestoreDataProviderProtocol {

    // MARK: - Properties

    var writtenCollection: String?
    var writtenId: String?
    var writtenDTO: FoodItemSubmissionDTO?
    var stubbedSetError: Error?
    var stubbedReReadDTO: FoodItemSubmissionDTO?
    var stubbedReReadError: Error?

    // MARK: - Functions

    func loadAsync<T: Decodable>(from collection: String) async throws -> [T] { [] }
    func loadFromServerAsync<T: Decodable>(from collection: String) async throws -> [T] { [] }
    func loadAsync<T: Decodable>(from collection: String, where field: String, isGreaterThanOrEqualTo lowerBound: Double, isLessThan upperBound: Double) async throws -> [T] { [] }
    func loadAsync<T: Decodable>(from collection: String, where field: String, hasPrefix prefix: String, limit: Int) async throws -> [T] { [] }
    func loadAsync<T: Decodable>(from collection: String, where field: String, arrayContains value: String, limit: Int) async throws -> [T] { [] }
    func loadAsync<T: Decodable>(from collection: String, where field: String, isEqualTo value: String) async throws -> T? { nil }
    func loadAsync<T: Decodable>(from collection: String, where field: String, isEqualTo value: String, orderBy orderField: String, descending: Bool) async throws -> [T] { [] }
    func loadAsync<T: Decodable>(id: String, from collection: String) async throws -> T? { nil }
    func loadFromServerAsync<T: Decodable>(id: String, from collection: String) async throws -> T? {
        if let stubbedReReadError { throw stubbedReReadError }
        return stubbedReReadDTO as? T
    }
    func loadAsync<T: Decodable>(from collection: String, orderBy field: String, descending: Bool, limit: Int) async throws -> [T] { [] }
    func saveAsync<T: Encodable>(_ item: T, to collection: String) async throws {}
    func setAsync<T: Encodable>(_ item: T, id: String, in collection: String) async throws {
        if let stubbedSetError { throw stubbedSetError }
        writtenCollection = collection
        writtenId = id
        writtenDTO = item as? FoodItemSubmissionDTO
    }
    func batchSetAsync<T: Encodable>(_ items: [(item: T, id: String)], in collection: String) async throws {}
    func deleteAsync(id: String, from collection: String) async throws {}
}
