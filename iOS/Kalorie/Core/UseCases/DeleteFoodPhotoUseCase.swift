//
//  DeleteFoodPhotoUseCase.swift
//  Kalorie
//
//  Created by Josef Antoni on 08.10.2026.
//

import Foundation

protocol DeleteFoodPhotoUseCaseProtocol {
    func callAsFunction(url: URL) async throws
}

extension DeleteFoodPhotoUseCaseProtocol {

    // MARK: - Functions

    func deleteQuietly(_ url: URL) async {
        do {
            try await self(url: url)
        } catch {
            Log.error(error, category: Constants.LogCategory.storage)
        }
    }
}

struct DeleteFoodPhotoUseCase: DeleteFoodPhotoUseCaseProtocol {

    // MARK: - Properties

    private let storageProvider: any StorageDataProviderProtocol

    // MARK: - Init

    init(storageProvider: any StorageDataProviderProtocol) {
        self.storageProvider = storageProvider
    }

    // MARK: - Functions

    func callAsFunction(url: URL) async throws {
        try await storageProvider.deleteAsync(url: url)
    }
}

#if DEBUG
struct DeleteFoodPhotoUseCaseFake: DeleteFoodPhotoUseCaseProtocol {

    // MARK: - Properties

    var shouldThrow = false

    // MARK: - Functions

    func callAsFunction(url: URL) async throws {
        if shouldThrow { throw URLError(.unknown) }
    }
}
#endif
