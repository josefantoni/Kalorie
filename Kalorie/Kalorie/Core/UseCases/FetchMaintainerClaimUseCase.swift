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

    struct Entry {
        let value: Bool
        let cachedAt: Date
    }

    var entry: Entry?

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
            let entry = cache.entry,
            Date.now.timeIntervalSince(entry.cachedAt) < Constants.Auth.maintainerClaimCacheTTL
        {
            return entry.value
        }
        guard let user = Auth.auth().currentUser else { throw AuthError.notAuthenticated }
        let result = try await user.getIDTokenResult(forcingRefresh: cache.entry != nil)
        let isMaintainer = result.claims["maintainer"] as? Bool ?? false
        cache.entry = MaintainerClaimCache.Entry(value: isMaintainer, cachedAt: .now)
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
