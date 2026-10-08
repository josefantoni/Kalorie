//
//  UpdateMySubmissionUseCase.swift
//  Kalorie
//
//  Created by Josef Antoni on 10.09.2026.
//

import Foundation

protocol UpdateMySubmissionUseCaseProtocol {
    func callAsFunction(id: String, item: FoodItemDomain, photo: FoodItemFormPhoto, previousPhotoURL: URL?) async throws -> FoodItemSubmissionDomain
}

struct UpdateMySubmissionUseCase: UpdateMySubmissionUseCaseProtocol {

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

    func callAsFunction(id: String, item: FoodItemDomain, photo: FoodItemFormPhoto, previousPhotoURL: URL?) async throws -> FoodItemSubmissionDomain {
        try await FoodItemSubmissionWriter(
            dataProvider: dataProvider,
            authProvider: authProvider,
            uploadFoodPhoto: uploadFoodPhoto,
            deleteFoodPhoto: deleteFoodPhoto
        ).write(
            id: id,
            item: item,
            photo: photo,
            previousPhotoURL: previousPhotoURL
        )
    }
}

#if DEBUG
struct UpdateMySubmissionUseCaseFake: UpdateMySubmissionUseCaseProtocol {

    // MARK: - Properties

    var errorToThrow: Error?

    // MARK: - Functions

    func callAsFunction(id: String, item: FoodItemDomain, photo: FoodItemFormPhoto, previousPhotoURL: URL?) async throws -> FoodItemSubmissionDomain {
        if let errorToThrow { throw errorToThrow }
        return FoodItemSubmissionDomain(
            id: id,
            barcode: item.id,
            submittedBy: "test-user-id",
            status: .pending,
            submittedAt: .now,
            rejectReason: nil,
            item: item
        )
    }
}
#endif
