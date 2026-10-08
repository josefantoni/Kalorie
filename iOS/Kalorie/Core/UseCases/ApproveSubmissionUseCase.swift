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
    case photoMissing
}

protocol ApproveSubmissionUseCaseProtocol {
    func callAsFunction(submission: FoodItemSubmissionDomain, item: FoodItemDomain, photo: FoodItemFormPhoto) async throws
}

struct ApproveSubmissionUseCase: ApproveSubmissionUseCaseProtocol {

    // MARK: - Properties

    private let dataProvider: any FirestoreDataProviderProtocol
    private let authProvider: any AuthProviderProtocol
    private let createFoodItem: any CreateFoodItemUseCaseProtocol
    private let downloadFoodPhoto: any DownloadFoodPhotoUseCaseProtocol
    private let uploadFoodPhoto: any UploadFoodPhotoUseCaseProtocol
    private let deleteFoodPhoto: any DeleteFoodPhotoUseCaseProtocol

    // MARK: - Init

    init(
        dataProvider: any FirestoreDataProviderProtocol,
        authProvider: any AuthProviderProtocol,
        createFoodItem: any CreateFoodItemUseCaseProtocol,
        downloadFoodPhoto: any DownloadFoodPhotoUseCaseProtocol,
        uploadFoodPhoto: any UploadFoodPhotoUseCaseProtocol,
        deleteFoodPhoto: any DeleteFoodPhotoUseCaseProtocol
    ) {
        self.dataProvider = dataProvider
        self.authProvider = authProvider
        self.createFoodItem = createFoodItem
        self.downloadFoodPhoto = downloadFoodPhoto
        self.uploadFoodPhoto = uploadFoodPhoto
        self.deleteFoodPhoto = deleteFoodPhoto
    }

    // MARK: - Functions

    func callAsFunction(submission: FoodItemSubmissionDomain, item: FoodItemDomain, photo: FoodItemFormPhoto) async throws {
        guard authProvider.userId != nil else { throw AuthError.notAuthenticated }
        guard photo != .none else { throw ApproveSubmissionError.photoMissing }
        // The catalogue gets its own copy: the author keeps delete rights on their submission folder,
        // so pointing the catalogue at that file would let them remove a photo every user sees.
        let photoData = try await bytes(of: photo)
        let catalogueURL = try await uploadFoodPhoto(data: photoData, folder: "\(Constants.Storage.catalogPhotosFolder)/\(item.id)")
        do {
            // The review screen may have been open for a while — re-read right before creating so an
            // edit the author resubmitted in the meantime is never silently overwritten by the
            // maintainer's (now stale) form values. Done after the slow photo transfer to keep the
            // window between this check and the write as short as possible.
            let current: FoodItemSubmissionDTO? = try await dataProvider.loadFromServerAsync(
                id: submission.id,
                from: Constants.Firestore.foodItemSubmissions
            )
            guard let current else { throw ApproveSubmissionError.alreadyResolved }
            guard current.submittedAt == submission.submittedAt.timeIntervalSince1970 else {
                throw ApproveSubmissionError.changedSinceReview
            }
            _ = try await createFoodItem(item.withPhotoURL(catalogueURL))
        } catch {
            await deleteFoodPhoto.deleteQuietly(catalogueURL)
            throw error
        }
        do {
            try await dataProvider.deleteAsync(id: submission.id, from: Constants.Firestore.foodItemSubmissions)
            if let submissionPhotoURL = submission.item.photoURL {
                await deleteFoodPhoto.deleteQuietly(submissionPhotoURL)
            }
        } catch {
            // The catalogue write already succeeded — the important half of this operation is
            // done. A submission that survives this delete failure resurfaces as a collision on
            // the queue's next load (ModerationQueueViewModel) and is cleared by hand, exactly as
            // design 0009's own Risks table accepts.
            Log.error(error, category: Constants.LogCategory.moderation)
        }
    }

    // MARK: - Private

    private func bytes(of photo: FoodItemFormPhoto) async throws -> Data {
        switch photo {
        case .local(let data): data
        case .remote(let url): try await downloadFoodPhoto(url: url)
        case .none: throw ApproveSubmissionError.photoMissing
        }
    }
}

#if DEBUG
struct ApproveSubmissionUseCaseFake: ApproveSubmissionUseCaseProtocol {

    // MARK: - Properties

    var errorToThrow: Error?

    // MARK: - Functions

    func callAsFunction(submission: FoodItemSubmissionDomain, item: FoodItemDomain, photo: FoodItemFormPhoto) async throws {
        if let errorToThrow { throw errorToThrow }
    }
}
#endif
