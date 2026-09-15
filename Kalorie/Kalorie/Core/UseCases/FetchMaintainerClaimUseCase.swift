//
//  FetchMaintainerClaimUseCase.swift
//  Kalorie
//
//  Created by Josef Antoni on 10.09.2026.
//

import FirebaseAuth
import Foundation

protocol FetchMaintainerClaimUseCaseProtocol {
    func callAsFunction() async throws -> Bool
}

final class MaintainerClaimCache {

    // MARK: - Properties

    var value: Bool?
    var cachedAt: Date?

    // MARK: - Init

    init() {}
}

struct FetchMaintainerClaimUseCase: FetchMaintainerClaimUseCaseProtocol {

    // MARK: - Properties

    private let cache: MaintainerClaimCache

    // MARK: - Init

    init(cache: MaintainerClaimCache) {
        self.cache = cache
    }

    // MARK: - Functions

    func callAsFunction() async throws -> Bool {
        if
            let cachedValue = cache.value,
            let cachedAt = cache.cachedAt,
            Date.now.timeIntervalSince(cachedAt) < Constants.Auth.maintainerClaimCacheTTL
        {
            return cachedValue
        }
        guard let user = Auth.auth().currentUser else { throw AuthError.notAuthenticated }
        let result = try await user.getIDTokenResult(forcingRefresh: cache.value != nil)
        let isMaintainer = result.claims["maintainer"] as? Bool ?? false
        cache.value = isMaintainer
        cache.cachedAt = .now
        return isMaintainer
    }
}

#if DEBUG
struct FetchMaintainerClaimUseCaseFake: FetchMaintainerClaimUseCaseProtocol {

    // MARK: - Properties

    var stubbedIsMaintainer = false

    // MARK: - Functions

    func callAsFunction() async throws -> Bool {
        stubbedIsMaintainer
    }
}
#endif
