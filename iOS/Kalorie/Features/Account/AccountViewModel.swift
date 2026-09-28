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
    @Published var isReauthenticateAlertVisible = false

    private let authProvider: any AuthProviderProtocol
    private let signOut: any SignOutUseCaseProtocol
    private let signInWithApple: any SignInWithAppleUseCaseProtocol
    private let signInWithGoogle: any SignInWithGoogleUseCaseProtocol
    private let deleteAccount: any DeleteAccountUseCaseProtocol
    private let reauthenticate: any ReauthenticateUseCaseProtocol
    private let mergeStatusReporting: any MergeStatusReporting
    private var isDataAlreadyWiped = false

    var isAnonymous: Bool { authProvider.isAnonymous }
    var displayName: String? { authProvider.displayName }

    // MARK: - Init

    init(
        authProvider: any AuthProviderProtocol,
        signOut: any SignOutUseCaseProtocol,
        signInWithApple: any SignInWithAppleUseCaseProtocol,
        signInWithGoogle: any SignInWithGoogleUseCaseProtocol,
        deleteAccount: any DeleteAccountUseCaseProtocol,
        reauthenticate: any ReauthenticateUseCaseProtocol,
        mergeStatusReporting: any MergeStatusReporting
    ) {
        self.authProvider = authProvider
        self.signOut = signOut
        self.signInWithApple = signInWithApple
        self.signInWithGoogle = signInWithGoogle
        self.deleteAccount = deleteAccount
        self.reauthenticate = reauthenticate
        self.mergeStatusReporting = mergeStatusReporting
    }

    // MARK: - Functions

    func onSignOutTapped() {
        do {
            try signOut()
        } catch {
            Log.error(error, category: Constants.LogCategory.account)
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
        } catch where isUserCancellation(error) {
        } catch {
            Log.error(error, category: Constants.LogCategory.account)
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
        } catch where isUserCancellation(error) {
        } catch {
            Log.error(error, category: Constants.LogCategory.account)
            alertItem = AlertItem(title: L10n.Account.errorSignInFailed)
        }
    }

    @MainActor
    func onDeleteAccountConfirmed() async {
        isDataAlreadyWiped = false
        defer { state = .idle }
        await performDelete()
    }

    @MainActor
    func onReauthenticateConfirmed() async {
        state = .linking
        defer { state = .idle }
        do {
            try await reauthenticate()
            await performDelete()
        } catch where isUserCancellation(error) {
        } catch {
            Log.error(error, category: Constants.LogCategory.account)
            alertItem = AlertItem(title: L10n.Account.errorSignInFailed)
        }
    }

    // MARK: - Private

    private func isUserCancellation(_ error: Error) -> Bool {
        if
            let authError = error as? ASAuthorizationError,
            authError.code == .canceled
        {
            return true
        }
        let nsError = error as NSError
        return nsError.domain == GIDSignInError.errorDomain && nsError.code == GIDSignInError.canceled.rawValue
    }

    @MainActor
    private func performDelete() async {
        state = .deletingAccount
        do {
            try await deleteAccount(skipDataWipe: isDataAlreadyWiped)
        } catch DeleteAccountError.requiresRecentLogin(let dataAlreadyDeleted) {
            isDataAlreadyWiped = dataAlreadyDeleted
            isReauthenticateAlertVisible = true
        } catch {
            Log.error(error, category: Constants.LogCategory.account)
            alertItem = AlertItem(title: L10n.Account.errorDeleteFailed)
        }
    }
}
