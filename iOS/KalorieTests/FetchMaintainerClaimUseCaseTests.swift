//
//  FetchMaintainerClaimUseCaseTests.swift
//  KalorieTests
//

import XCTest
@testable import Kalorie

final class FetchMaintainerClaimUseCaseTests: XCTestCase {

    func test_staleEntry_withoutForcedRefresh_readsTheCachedToken() async throws {
        let (sut, authProvider) = makeSUT(forcesRefresh: false, entryAge: staleAge)

        _ = try await sut()

        XCTAssertEqual(authProvider.maintainerClaimRequests.forcingRefreshValues, [false])
    }

    func test_staleEntry_withForcedRefresh_refreshesTheToken() async throws {
        let (sut, authProvider) = makeSUT(forcesRefresh: true, entryAge: staleAge)

        _ = try await sut()

        XCTAssertEqual(authProvider.maintainerClaimRequests.forcingRefreshValues, [true])
    }

    func test_emptyCache_neverForcesRefresh() async throws {
        for forcesRefresh in [false, true] {
            let (sut, authProvider) = makeSUT(forcesRefresh: forcesRefresh, entryAge: nil)

            _ = try await sut()

            XCTAssertEqual(authProvider.maintainerClaimRequests.forcingRefreshValues, [false])
        }
    }

    func test_freshEntry_doesNotCallTheProvider() async throws {
        let (sut, authProvider) = makeSUT(forcesRefresh: true, entryAge: 1)

        _ = try await sut()

        XCTAssertTrue(authProvider.maintainerClaimRequests.forcingRefreshValues.isEmpty)
    }

    func test_missingUser_throwsNotAuthenticated() async {
        let (sut, _) = makeSUT(forcesRefresh: false, entryAge: nil, userId: nil)

        do {
            _ = try await sut()
            XCTFail("Expected AuthError.notAuthenticated")
        } catch {
            XCTAssertEqual(error as? AuthError, .notAuthenticated)
        }
    }

    // MARK: - Helpers

    private var staleAge: TimeInterval { Constants.Auth.maintainerClaimCacheTTL + 1 }

    private func makeSUT(
        forcesRefresh: Bool,
        entryAge: TimeInterval?,
        userId: String? = "user-1"
    ) -> (sut: FetchMaintainerClaimUseCase, authProvider: AuthProviderFake) {
        let authProvider = AuthProviderFake(userId: userId)
        let cache = MaintainerClaimCache()
        if let entryAge {
            cache.entry = MaintainerClaimCache.Entry(
                value: true,
                cachedAt: Date.now.addingTimeInterval(-entryAge),
                userId: "user-1"
            )
        }
        let sut = FetchMaintainerClaimUseCase(authProvider: authProvider, cache: cache, forcesRefresh: forcesRefresh)
        return (sut, authProvider)
    }
}
