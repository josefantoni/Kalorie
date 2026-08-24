//
//  AccountViewModel.swift
//  Kalorie
//
//  Created by Josef Antoni on 07.08.2026.
//

import AuthenticationServices
import Foundation
import GoogleSignIn

final class AccountViewModel: ObservableObject {

    // MARK: - State

    enum State {
        case idle
        case linking
        case deletingAccount
    }

    // MARK: - Properties

    @Published private(set) var state: State = .idle
    @Published var alertItem: AlertItem?
    @Published var showDeleteConfirmation = false

    private let authProvider: any AuthProviderProtocol
    private let signOut: any SignOutUseCaseProtocol
    private let signInWithApple: any SignInWithAppleUseCaseProtocol
    private let signInWithGoogle: any SignInWithGoogleUseCaseProtocol
    private let deleteAccount: any DeleteAccountUseCaseProtocol
    private let mergeStatusReporting: any MergeStatusReporting

    var isAnonymous: Bool { authProvider.isAnonymous }
    var displayName: String? { authProvider.displayName }

    // MARK: - Init

    init(
        authProvider: any AuthProviderProtocol,
        signOut: any SignOutUseCaseProtocol,
        signInWithApple: any SignInWithAppleUseCaseProtocol,
        signInWithGoogle: any SignInWithGoogleUseCaseProtocol,
        deleteAccount: any DeleteAccountUseCaseProtocol,
        mergeStatusReporting: any MergeStatusReporting
    ) {
        self.authProvider = authProvider
        self.signOut = signOut
        self.signInWithApple = signInWithApple
        self.signInWithGoogle = signInWithGoogle
        self.deleteAccount = deleteAccount
        self.mergeStatusReporting = mergeStatusReporting
    }

    // MARK: - Functions

    func onSignOutTapped() {
        do {
            try signOut()
        } catch {
            alertItem = AlertItem(title: L10n.Account.errorSignOutFailed)
        }
    }

    @MainActor
    func onSignInWithAppleTapped() async {
        state = .linking
        mergeStatusReporting.beginMerge()
        defer {
            mergeStatusReporting.endMerge()
            state = .idle
        }
        do {
            try await signInWithApple()
        } catch let error as ASAuthorizationError where error.code == .canceled {
        } catch {
            alertItem = AlertItem(title: L10n.Account.errorSignInFailed)
        }
    }

    @MainActor
    func onSignInWithGoogleTapped() async {
        state = .linking
        mergeStatusReporting.beginMerge()
        defer {
            mergeStatusReporting.endMerge()
            state = .idle
        }
        do {
            try await signInWithGoogle()
        } catch LinkOrMergeCredentialError.accountExistsWithAnotherProvider {
            alertItem = AlertItem(title: L10n.Account.errorAccountExistsWithApple)
        } catch let error as NSError where error.code == GIDSignInError.canceled.rawValue {
        } catch {
            alertItem = AlertItem(title: L10n.Account.errorSignInFailed)
        }
    }

    @MainActor
    func onDeleteAccountConfirmed() async {
        state = .deletingAccount
        do {
            try await deleteAccount()
        } catch DeleteAccountError.requiresRecentLogin {
            alertItem = AlertItem(title: L10n.Account.errorDeleteRequiresRecentLogin)
        } catch {
            alertItem = AlertItem(title: L10n.Account.errorDeleteFailed)
        }
        state = .idle
    }
}
