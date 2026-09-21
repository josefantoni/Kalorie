//
//  RejectSubmissionUseCase.swift
//  Kalorie
//
//  Created by Josef Antoni on 10.09.2026.
//

import FirebaseFirestore
import Foundation

enum RejectSubmissionError: Error {
    case reasonRequired
    case alreadyResolved
    case changedSinceReview
}

protocol RejectSubmissionUseCaseProtocol {
    func callAsFunction(_ submission: FoodItemSubmissionDomain, reason: String) async throws
}

struct RejectSubmissionUseCase: RejectSubmissionUseCaseProtocol {

    // MARK: - Properties

    private let dataProvider: any FirestoreDataProviderProtocol
    private let authProvider: any AuthProviderProtocol

    // MARK: - Init

    init(dataProvider: any FirestoreDataProviderProtocol, authProvider: any AuthProviderProtocol) {
        self.dataProvider = dataProvider
        self.authProvider = authProvider
    }

    // MARK: - Functions

    func callAsFunction(_ submission: FoodItemSubmissionDomain, reason: String) async throws {
        guard authProvider.userId != nil else { throw AuthError.notAuthenticated }
        let trimmedReason = reason.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmedReason.isEmpty else { throw RejectSubmissionError.reasonRequired }
        let dto = FoodItemSubmissionDTO(
            id: submission.id,
            barcode: submission.barcode,
            submittedBy: submission.submittedBy,
            status: .rejected,
            submittedAt: submission.submittedAt,
            rejectReason: trimmedReason,
            item: submission.item
        )
        do {
            try await dataProvider.setAsync(dto, id: submission.id, in: Constants.Firestore.foodItemSubmissions)
        } catch let writeError where writeError.matches(domain: FirestoreErrorDomain, code: FirestoreErrorCode.permissionDenied.rawValue) {
            // The rules deny three different things with the same code: "someone already
            // approved/deleted this submission", "the author resubmitted it since this screen
            // loaded it" (the rule requires submitted_at to be unchanged, see firestore.rules) and
            // an expired auth session. Re-read to tell them apart. If the re-read itself fails, we
            // can't tell, so surface the original write error rather than misreporting it.
            let current: FoodItemSubmissionDTO?
            do {
                current = try await dataProvider.loadFromServerAsync(
                    id: submission.id,
                    from: Constants.Firestore.foodItemSubmissions
                )
            } catch {
                throw writeError
            }
            guard let current else { throw RejectSubmissionError.alreadyResolved }
            guard current.submittedAt == submission.submittedAt.timeIntervalSince1970 else {
                throw RejectSubmissionError.changedSinceReview
            }
            throw writeError
        }
    }
}

#if DEBUG
struct RejectSubmissionUseCaseFake: RejectSubmissionUseCaseProtocol {

    // MARK: - Properties

    var errorToThrow: Error?

    // MARK: - Functions

    func callAsFunction(_ submission: FoodItemSubmissionDomain, reason: String) async throws {
        if let errorToThrow { throw errorToThrow }
    }
}
#endif
