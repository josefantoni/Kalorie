//
//  WithdrawUserNotificationUseCaseTests.swift
//  KalorieTests
//
//  Created by Josef Antoni on 14.11.2026.
//

import FirebaseFirestore
import FirebaseStorage
import XCTest
@testable import Kalorie

final class WithdrawUserNotificationUseCaseTests: XCTestCase {

    // MARK: - Tests

    func test_withdraw_aPendingReport_deletesItsDocument() async throws {
        let (sut, dataProvider, _) = makeSUT()
        try await sut(makeNotification(id: "report-3"))
        XCTAssertEqual(dataProvider.deletes.map(\.id), ["report-3"])
        XCTAssertEqual(dataProvider.deletes.first?.collection, Constants.Firestore.foodItemReports)
    }

    func test_withdraw_aPendingPhoto_deletesTheFileBeforeTheDocument() async throws {
        let (sut, dataProvider, storage) = makeSUT()
        let notification = makeNotification(kind: .photoSuggestion, id: "photo-3")
        let url = try XCTUnwrap(notification.photoURL)
        storage.files[url] = Data([1])
        try await sut(notification)
        XCTAssertEqual(storage.deletedURLs, [url], "the path comes from the stored photo_url — an anonymised ticket has no uid to build it from")
        XCTAssertEqual(dataProvider.deletes.map(\.id), ["photo-3"])
    }

    func test_withdraw_aPhotoWhoseFileIsAlreadyGone_stillDeletesTheDocument() async throws {
        let (sut, dataProvider, storage) = makeSUT()
        storage.deleteError = NSError(domain: StorageErrorDomain, code: StorageErrorCode.objectNotFound.rawValue)
        try await sut(makeNotification(kind: .photoSuggestion, id: "photo-3"))
        XCTAssertEqual(dataProvider.deletes.map(\.id), ["photo-3"])
    }

    func test_withdraw_whenTheFileDeleteFails_keepsTheDocumentForARetry() async throws {
        let (sut, dataProvider, storage) = makeSUT()
        storage.deleteError = URLError(.notConnectedToInternet)
        do {
            try await sut(makeNotification(kind: .photoSuggestion))
            XCTFail("the failure must reach the caller")
        } catch {}
        XCTAssertTrue(dataProvider.deletes.isEmpty, "deleting the document first would orphan the file")
    }

    func test_withdraw_aResolvedTicket_isRefusedWithoutDeleting() async throws {
        let (sut, dataProvider, _) = makeSUT()
        do {
            try await sut(makeNotification(status: .accepted, resolvedAt: .now))
            XCTFail("a resolved ticket is the record of what the maintainer did")
        } catch WithdrawUserNotificationError.alreadyResolved {}
        XCTAssertTrue(dataProvider.deletes.isEmpty)
    }

    func test_withdraw_whenTheMaintainerResolvedItMeanwhile_throwsAlreadyResolved() async throws {
        let (sut, dataProvider, _) = makeSUT()
        dataProvider.failingWriteCollections = [Constants.Firestore.foodItemReports]
        dataProvider.failingError = NSError(domain: FirestoreErrorDomain, code: FirestoreErrorCode.permissionDenied.rawValue)
        do {
            try await sut(makeNotification())
            XCTFail("PERMISSION_DENIED on a pending delete means the rules saw a resolved ticket")
        } catch WithdrawUserNotificationError.alreadyResolved {}
    }

    func test_withdraw_whenTheDeleteFailsForAnotherReason_rethrowsIt() async throws {
        let (sut, dataProvider, _) = makeSUT()
        dataProvider.failingWriteCollections = [Constants.Firestore.foodItemReports]
        do {
            try await sut(makeNotification())
            XCTFail("Expected the original error")
        } catch {
            XCTAssertEqual((error as? URLError)?.code, .notConnectedToInternet)
        }
    }

    // MARK: - Helpers

    private func makeSUT(userId: String? = "test-user") -> (sut: WithdrawUserNotificationUseCase, dataProvider: TicketDataProviderFake, storage: StorageDataProviderFake) {
        let dataProvider = TicketDataProviderFake()
        let storage = StorageDataProviderFake()
        let sut = WithdrawUserNotificationUseCase(
            dataProvider: dataProvider,
            authProvider: AuthProviderFake(userId: userId),
            storageProvider: storage
        )
        return (sut, dataProvider, storage)
    }
}
