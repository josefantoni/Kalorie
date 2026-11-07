//
//  FetchMaintainerClaimUseCase.swift
//  Kalorie
//
//  Created by Josef Antoni on 10.09.2026.
//

import Foundation

protocol FetchMaintainerClaimUseCaseProtocol {
    func callAsFunction() async throws -> Bool
}

final class MaintainerClaimCache {

    // MARK: - Properties

    struct Entry {
        let value: Bool
        let cachedAt: Date
        let userId: String
    }

    var entry: Entry?

    // MARK: - Init

    init() {}

    // MARK: - Functions

    func freshValue(userId: String, now: Date, timeToLive: TimeInterval) -> Bool? {
        guard
            let entry,
            entry.userId == userId,
            now.timeIntervalSince(entry.cachedAt) < timeToLive
        else { return nil }
        return entry.value
    }
}

struct FetchMaintainerClaimUseCase: FetchMaintainerClaimUseCaseProtocol {

    // MARK: - Properties

    private let authProvider: any AuthProviderProtocol
    private let cache: MaintainerClaimCache
    private let forcesRefresh: Bool

    // MARK: - Init

    init(authProvider: any AuthProviderProtocol, cache: MaintainerClaimCache, forcesRefresh: Bool) {
        self.authProvider = authProvider
        self.cache = cache
        self.forcesRefresh = forcesRefresh
    }

    // MARK: - Functions

    func callAsFunction() async throws -> Bool {
        guard let userId = authProvider.userId else { throw AuthError.notAuthenticated }
        if let value = cache.freshValue(userId: userId, now: .now, timeToLive: Constants.Auth.maintainerClaimCacheTTL) {
            return value
        }
        let isMaintainer = try await authProvider.maintainerClaim(forcingRefresh: forcesRefresh && cache.entry != nil)
        cache.entry = MaintainerClaimCache.Entry(value: isMaintainer, cachedAt: .now, userId: userId)
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
