//
//  UploadFoodPhotoUseCase.swift
//  Kalorie
//
//  Created by Josef Antoni on 08.10.2026.
//

import Foundation

protocol UploadFoodPhotoUseCaseProtocol {
    func callAsFunction(data: Data, folder: String) async throws -> URL
}

struct UploadFoodPhotoUseCase: UploadFoodPhotoUseCaseProtocol {

    // MARK: - Properties

    private let storageProvider: any StorageDataProviderProtocol

    // MARK: - Init

    init(storageProvider: any StorageDataProviderProtocol) {
        self.storageProvider = storageProvider
    }

    // MARK: - Functions

    func callAsFunction(data: Data, folder: String) async throws -> URL {
        try await storageProvider.uploadAsync(
            data: data,
            path: "\(folder)/\(UUID().uuidString).jpg",
            contentType: Constants.Storage.photoContentType
        )
    }
}
