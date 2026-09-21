//
//  ApproveSubmissionUseCase.swift
//  Kalorie
//
//  Created by Josef Antoni on 10.09.2026.
//

import Foundation

enum ApproveSubmissionError: Error {
    case alreadyResolved
    case changedSinceReview
}

protocol ApproveSubmissionUseCaseProtocol {
    func callAsFunction(submission: FoodItemSubmissionDomain, item: FoodItemDomain) async throws
}

struct ApproveSubmissionUseCase: ApproveSubmissionUseCaseProtocol {

    // MARK: - Properties

    private let dataProvider: any FirestoreDataProviderProtocol
    private let authProvider: any AuthProviderProtocol
    private let createFoodItem: any CreateFoodItemUseCaseProtocol

    // MARK: - Init

    init(
        dataProvider: any FirestoreDataProviderProtocol,
        authProvider: any AuthProviderProtocol,
        createFoodItem: any CreateFoodItemUseCaseProtocol
    ) {
        self.dataProvider = dataProvider
        self.authProvider = authProvider
        self.createFoodItem = createFoodItem
    }

    // MARK: - Functions

    func callAsFunction(submission: FoodItemSubmissionDomain, item: FoodItemDomain) async throws {
        guard authProvider.userId != nil else { throw AuthError.notAuthenticated }
        // The review screen may have been open for a while — re-read before approving so an
        // edit the author resubmitted in the meantime is never silently overwritten by the
        // maintainer's (now stale) form values.
        let current: FoodItemSubmissionDTO? = try await dataProvider.loadFromServerAsync(
            id: submission.id,
            from: Constants.Firestore.foodItemSubmissions
        )
        guard let current else { throw ApproveSubmissionError.alreadyResolved }
        guard current.submittedAt == submission.submittedAt.timeIntervalSince1970 else {
            throw ApproveSubmissionError.changedSinceReview
        }
        _ = try await createFoodItem(item)
        do {
            try await dataProvider.deleteAsync(id: submission.id, from: Constants.Firestore.foodItemSubmissions)
        } catch {
            // The catalogue write already succeeded — the important half of this operation is
            // done. A submission that survives this delete failure resurfaces as a collision on
            // the queue's next load (ModerationQueueViewModel) and is cleared by hand, exactly as
            // design 0009's own Risks table accepts.
            Log.error(error, category: Constants.LogCategory.moderation)
        }
    }
}

#if DEBUG
struct ApproveSubmissionUseCaseFake: ApproveSubmissionUseCaseProtocol {

    // MARK: - Properties

    var errorToThrow: Error?

    // MARK: - Functions

    func callAsFunction(submission: FoodItemSubmissionDomain, item: FoodItemDomain) async throws {
        if let errorToThrow { throw errorToThrow }
    }
}
#endif
