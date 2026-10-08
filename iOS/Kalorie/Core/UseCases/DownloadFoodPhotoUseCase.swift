//
//  DownloadFoodPhotoUseCase.swift
//  Kalorie
//
//  Created by Josef Antoni on 08.10.2026.
//

import Foundation

protocol DownloadFoodPhotoUseCaseProtocol {
    func callAsFunction(url: URL) async throws -> Data
}

struct DownloadFoodPhotoUseCase: DownloadFoodPhotoUseCaseProtocol {

    // MARK: - Properties

    private let storageProvider: any StorageDataProviderProtocol

    // MARK: - Init

    init(storageProvider: any StorageDataProviderProtocol) {
        self.storageProvider = storageProvider
    }

    // MARK: - Functions

    func callAsFunction(url: URL) async throws -> Data {
        try await storageProvider.downloadAsync(url: url)
    }
}

#if DEBUG
struct DownloadFoodPhotoUseCaseFake: DownloadFoodPhotoUseCaseProtocol {

    // MARK: - Properties

    var result = Data([1])
    var shouldThrow = false

    // MARK: - Functions

    func callAsFunction(url: URL) async throws -> Data {
        if shouldThrow { throw URLError(.unknown) }
        return result
    }
}
#endif
