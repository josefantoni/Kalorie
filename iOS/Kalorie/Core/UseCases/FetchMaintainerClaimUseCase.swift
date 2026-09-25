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

    private let cache: MaintainerClaimCache

    // MARK: - Init

    init(cache: MaintainerClaimCache) {
        self.cache = cache
    }

    // MARK: - Functions

    func callAsFunction() async throws -> Bool {
        guard let user = Auth.auth().currentUser else { throw AuthError.notAuthenticated }
        if let value = cache.freshValue(userId: user.uid, now: .now, timeToLive: Constants.Auth.maintainerClaimCacheTTL) {
            return value
        }
        let result = try await user.getIDTokenResult(forcingRefresh: cache.entry != nil)
        let isMaintainer = result.claims["maintainer"] as? Bool ?? false
        cache.entry = MaintainerClaimCache.Entry(value: isMaintainer, cachedAt: .now, userId: user.uid)
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
