//
//  FetchMinimumVersionUseCase.swift
//  Kalorie
//
//  Created by Josef Antoni on 21.10.2026.
//

import Foundation

protocol FetchMinimumVersionUseCaseProtocol {
    func callAsFunction() async throws -> AppVersion?
}

struct FetchMinimumVersionUseCase: FetchMinimumVersionUseCaseProtocol {

    // MARK: - Properties

    private let dataProvider: any FirestoreDataProviderProtocol

    // MARK: - Init

    init(dataProvider: any FirestoreDataProviderProtocol) {
        self.dataProvider = dataProvider
    }

    // MARK: - Functions

    func callAsFunction() async throws -> AppVersion? {
        let config: AppConfigDTO? = try await dataProvider.loadFromServerAsync(
            id: Constants.Firestore.appConfigDocumentId,
            from: Constants.Firestore.config
        )
        return config?.minVersionIos.flatMap(AppVersion.init)
    }
}

#if DEBUG
struct FetchMinimumVersionUseCaseFake: FetchMinimumVersionUseCaseProtocol {

    // MARK: - Properties

    var stubbedMinimum: AppVersion?
    var shouldThrow = false

    // MARK: - Functions

    func callAsFunction() async throws -> AppVersion? {
        if shouldThrow { throw URLError(.unknown) }
        return stubbedMinimum
    }
}
#endif
