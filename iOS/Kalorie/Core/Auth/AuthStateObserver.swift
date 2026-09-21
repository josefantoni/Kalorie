//
//  AuthStateObserver.swift
//  Kalorie
//
//  Created by Josef Antoni on 30.06.2026.
//

import Foundation
import FirebaseAuth

protocol MergeStatusReporting: AnyObject {
    @MainActor
    func beginMerge()
    @MainActor
    func endMerge()
}

final class AuthStateObserver: ObservableObject, MergeStatusReporting {

    // MARK: - Properties

    @Published private(set) var state: LoadingState<Void> = .loading
    @Published private(set) var userId: String?
    @Published private(set) var isMerging = false

    private let resumePendingMerge: any MigrateAnonymousDataUseCaseProtocol
    private var hasAttemptedPendingMergeResume = false
    private var activeMergeCount = 0

    // MARK: - Init

    init(resumePendingMerge: any MigrateAnonymousDataUseCaseProtocol) {
        self.resumePendingMerge = resumePendingMerge
        Auth.auth().addStateDidChangeListener { [weak self] _, user in
            guard let self else { return }
            if let user {
                Task { @MainActor in
                    await self.resumePendingMergeOnce()
                    self.userId = user.uid
                    self.state = .loaded
                }
            } else {
                self.signIn()
            }
        }
    }

    // MARK: - Functions

    func retry() {
        state = .loading
        signIn()
    }

    // isMerging is a counter rather than a plain Bool: two independent places raise the signal
    // concurrently (this resume and the live merge started from AccountViewModel), and whichever
    // finishes first must not tear the overlay down while the other is still running.
    @MainActor
    func beginMerge() {
        activeMergeCount += 1
        isMerging = true
    }

    @MainActor
    func endMerge() {
        activeMergeCount = max(0, activeMergeCount - 1)
        isMerging = activeMergeCount > 0
    }

    // MARK: - Private

    // Resume is crash recovery for a merge left behind by a previous run, so it must run
    // only on the first resolved auth state. Without the guard, the sign-in performed inside
    // MigrateAnonymousDataUseCase.migrate() re-triggers this listener and runs the very same
    // merge a second time, concurrently with the migrate() call that is still in flight.
    @MainActor
    private func resumePendingMergeOnce() async {
        guard !hasAttemptedPendingMergeResume else { return }
        hasAttemptedPendingMergeResume = true
        beginMerge()
        do {
            try await resumePendingMerge.resumeIfNeeded()
        } catch {
            Log.error(error, category: Constants.LogCategory.auth)
        }
        endMerge()
    }

    private func signIn() {
        Auth.auth().signInAnonymously { [weak self] _, error in
            DispatchQueue.main.async {
                self?.state = error == nil ? .loaded : .error(error)
            }
        }
    }
}

#if DEBUG
final class MergeStatusReportingFake: MergeStatusReporting {

    // MARK: - Properties

    private(set) var beginMergeCallCount = 0
    private(set) var endMergeCallCount = 0

    // MARK: - Functions

    func beginMerge() { beginMergeCallCount += 1 }
    func endMerge() { endMergeCallCount += 1 }
}
#endif
