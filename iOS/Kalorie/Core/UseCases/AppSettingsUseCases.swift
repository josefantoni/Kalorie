//
//  AppSettingsUseCases.swift
//  Kalorie
//
//  Created by Josef Antoni on 27.10.2026.
//

import Foundation

protocol FetchAppSettingsUseCaseProtocol {
    func callAsFunction() async throws -> AppSettingsDomain
}

struct FetchAppSettingsUseCase: FetchAppSettingsUseCaseProtocol {

    // MARK: - Properties

    private let dataProvider: any FirestoreDataProviderProtocol
    private let authProvider: any AuthProviderProtocol

    // MARK: - Init

    init(dataProvider: any FirestoreDataProviderProtocol, authProvider: any AuthProviderProtocol) {
        self.dataProvider = dataProvider
        self.authProvider = authProvider
    }

    // MARK: - Functions

    func callAsFunction() async throws -> AppSettingsDomain {
        guard let userId = authProvider.userId else { throw AuthError.notAuthenticated }
        let dto: AppSettingsDTO? = try await dataProvider.loadAsync(
            id: Constants.Firestore.appSettingsDocumentId,
            from: Constants.Firestore.settings(userId: userId)
        )
        return dto?.asDomain() ?? .defaults
    }
}

protocol SaveAppSettingsUseCaseProtocol {
    func callAsFunction(_ settings: AppSettingsDomain) async throws
}

struct SaveAppSettingsUseCase: SaveAppSettingsUseCaseProtocol {

    // MARK: - Properties

    private let dataProvider: any FirestoreDataProviderProtocol
    private let authProvider: any AuthProviderProtocol

    // MARK: - Init

    init(dataProvider: any FirestoreDataProviderProtocol, authProvider: any AuthProviderProtocol) {
        self.dataProvider = dataProvider
        self.authProvider = authProvider
    }

    // MARK: - Functions

    func callAsFunction(_ settings: AppSettingsDomain) async throws {
        guard let userId = authProvider.userId else { throw AuthError.notAuthenticated }
        try await dataProvider.mergeAsync(
            AppSettingsDTO(settings: settings),
            id: Constants.Firestore.appSettingsDocumentId,
            in: Constants.Firestore.settings(userId: userId)
        )
    }
}

#if DEBUG
struct FetchAppSettingsUseCaseFake: FetchAppSettingsUseCaseProtocol {

    // MARK: - Properties

    var stubbedSettings = AppSettingsDomain.defaults
    var shouldThrow = false

    // MARK: - Functions

    func callAsFunction() async throws -> AppSettingsDomain {
        if shouldThrow { throw URLError(.unknown) }
        return stubbedSettings
    }
}

struct SaveAppSettingsUseCaseFake: SaveAppSettingsUseCaseProtocol {

    // MARK: - Properties

    var shouldThrow = false

    // MARK: - Functions

    func callAsFunction(_ settings: AppSettingsDomain) async throws {
        if shouldThrow { throw URLError(.unknown) }
    }
}
#endif
