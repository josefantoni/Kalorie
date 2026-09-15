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
            // The rules deny both "someone else already approved/deleted this submission" and an
            // expired auth session with the same code — re-read to tell them apart. If the re-read
            // itself fails, we can't tell, so surface the original write error rather than
            // misreporting it as alreadyResolved.
            let stillQueued: FoodItemSubmissionDTO?
            do {
                stillQueued = try await dataProvider.loadFromServerAsync(
                    id: submission.id,
                    from: Constants.Firestore.foodItemSubmissions
                )
            } catch {
                throw writeError
            }
            guard stillQueued != nil else { throw RejectSubmissionError.alreadyResolved }
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
