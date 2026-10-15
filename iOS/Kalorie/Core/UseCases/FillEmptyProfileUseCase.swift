//
//  FillEmptyProfileUseCase.swift
//  Kalorie
//
//  Created by Josef Antoni on 15.10.2026.
//

import Foundation

protocol FillEmptyProfileUseCaseProtocol {
    func callAsFunction(displayName: String?, email: String?) async
}

struct FillEmptyProfileUseCase: FillEmptyProfileUseCaseProtocol {

    // MARK: - Properties

    private let authProvider: any AuthProviderProtocol
    private let authCommandProvider: any AuthCommandProviderProtocol
    private let dataProvider: any FirestoreDataProviderProtocol

    // MARK: - Init

    init(
        authProvider: any AuthProviderProtocol,
        authCommandProvider: any AuthCommandProviderProtocol,
        dataProvider: any FirestoreDataProviderProtocol
    ) {
        self.authProvider = authProvider
        self.authCommandProvider = authCommandProvider
        self.dataProvider = dataProvider
    }

    // MARK: - Functions

    func callAsFunction(displayName: String?, email: String?) async {
        guard let userId = authProvider.userId else { return }

        let existing: UserProfileDTO?
        do {
            existing = try await dataProvider.loadAsync(id: userId, from: Constants.Firestore.users)
        } catch {
            Log.error(error, category: Constants.LogCategory.auth)
            return
        }

        let currentName = Self.nonEmpty(authProvider.displayName) ?? Self.nonEmpty(existing?.displayName)
        let currentEmail = Self.nonEmpty(existing?.email)
        let nameToFill = currentName == nil ? Self.nonEmpty(displayName) : nil
        let emailToFill = currentEmail == nil ? Self.nonEmpty(email) : nil

        guard nameToFill != nil || emailToFill != nil else { return }

        if
            let nameToFill,
            Self.nonEmpty(authProvider.displayName) == nil
        {
            do {
                try await authCommandProvider.updateDisplayName(nameToFill)
            } catch {
                Log.error(error, category: Constants.LogCategory.auth)
            }
        }

        let dto = UserProfileDTO(
            displayName: currentName ?? nameToFill,
            email: currentEmail ?? emailToFill
        )
        do {
            try await dataProvider.setAsync(dto, id: userId, in: Constants.Firestore.users)
        } catch {
            Log.error(error, category: Constants.LogCategory.auth)
        }
    }

    private static func nonEmpty(_ value: String?) -> String? {
        guard let value, !value.isEmpty else { return nil }
        return value
    }
}

#if DEBUG
struct FillEmptyProfileUseCaseFake: FillEmptyProfileUseCaseProtocol {

    // MARK: - Functions

    func callAsFunction(displayName: String?, email: String?) async {}
}
#endif
