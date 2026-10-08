//
//  FoodItemSubmissionWriter.swift
//  Kalorie
//
//  Created by Josef Antoni on 11.09.2026.
//

import Foundation

struct FoodItemSubmissionWriter {

    // MARK: - Properties

    private let dataProvider: any FirestoreDataProviderProtocol
    private let authProvider: any AuthProviderProtocol
    private let uploadFoodPhoto: any UploadFoodPhotoUseCaseProtocol
    private let deleteFoodPhoto: any DeleteFoodPhotoUseCaseProtocol

    // MARK: - Init

    init(
        dataProvider: any FirestoreDataProviderProtocol,
        authProvider: any AuthProviderProtocol,
        uploadFoodPhoto: any UploadFoodPhotoUseCaseProtocol,
        deleteFoodPhoto: any DeleteFoodPhotoUseCaseProtocol
    ) {
        self.dataProvider = dataProvider
        self.authProvider = authProvider
        self.uploadFoodPhoto = uploadFoodPhoto
        self.deleteFoodPhoto = deleteFoodPhoto
    }

    // MARK: - Functions

    func write(
        id: String,
        item: FoodItemDomain,
        photo: FoodItemFormPhoto,
        previousPhotoURL: URL?
    ) async throws -> FoodItemSubmissionDomain {
        guard let userId = authProvider.userId else { throw AuthError.notAuthenticated }
        let item = item.id.isEmpty ? item.withId(id) : item
        if let validationError = FoodItemValidation.validate(item) {
            throw FoodItemSubmissionError(validationError)
        }
        guard photo != .none else { throw FoodItemSubmissionError.photoMissing }
        let existing: FoodItemDTO? = try await dataProvider.loadFromServerAsync(
            id: item.id,
            from: Constants.Firestore.foodItems
        )
        guard existing == nil else { throw FoodItemSubmissionError.itemAlreadyExists }
        let uploadedURL = try await uploadIfLocal(photo, userId: userId)
        let submission = FoodItemSubmissionDomain(
            id: id,
            barcode: item.barcode,
            submittedBy: userId,
            status: .pending,
            submittedAt: .now,
            rejectReason: nil,
            item: item.withPhotoURL(uploadedURL ?? remoteURL(of: photo))
        )
        let dto = FoodItemSubmissionDTO(
            id: submission.id,
            barcode: submission.barcode,
            submittedBy: submission.submittedBy,
            status: submission.status,
            submittedAt: submission.submittedAt,
            rejectReason: submission.rejectReason,
            item: submission.item
        )
        do {
            try await dataProvider.setAsync(dto, id: submission.id, in: Constants.Firestore.foodItemSubmissions)
        } catch {
            if let uploadedURL { await deleteFoodPhoto.deleteQuietly(uploadedURL) }
            throw error
        }
        if uploadedURL != nil, let previousPhotoURL {
            await deleteFoodPhoto.deleteQuietly(previousPhotoURL)
        }
        return submission
    }

    // MARK: - Private

    private func uploadIfLocal(_ photo: FoodItemFormPhoto, userId: String) async throws -> URL? {
        guard case .local(let data) = photo else { return nil }
        do {
            return try await uploadFoodPhoto(data: data, folder: "\(Constants.Storage.submissionPhotosFolder)/\(userId)")
        } catch {
            Log.error(error, category: Constants.LogCategory.storage)
            throw FoodItemSubmissionError.photoUploadFailed
        }
    }

    private func remoteURL(of photo: FoodItemFormPhoto) -> URL? {
        guard case .remote(let url) = photo else { return nil }
        return url
    }
}
