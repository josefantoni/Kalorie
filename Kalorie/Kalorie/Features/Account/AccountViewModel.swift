//
//  AccountViewModel.swift
//  Kalorie
//
//  Created by Josef Antoni on 07.08.2026.
//

import AuthenticationServices
import Foundation

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

    private var currentNonce: String?

    private let authProvider: any AuthProviderProtocol
    private let signOut: any SignOutUseCaseProtocol
    private let signInWithApple: any SignInWithAppleUseCaseProtocol
    private let deleteAccount: any DeleteAccountUseCaseProtocol
    private let mergeStatusReporting: any MergeStatusReporting

    var isAnonymous: Bool { authProvider.isAnonymous }
    var displayName: String? { authProvider.displayName }

    // MARK: - Init

    init(
        authProvider: any AuthProviderProtocol,
        signOut: any SignOutUseCaseProtocol,
        signInWithApple: any SignInWithAppleUseCaseProtocol,
        deleteAccount: any DeleteAccountUseCaseProtocol,
        mergeStatusReporting: any MergeStatusReporting
    ) {
        self.authProvider = authProvider
        self.signOut = signOut
        self.signInWithApple = signInWithApple
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

    func onSignInRequest(_ request: ASAuthorizationAppleIDRequest) {
        let nonce = NonceGenerator.randomNonceString()
        currentNonce = nonce
        request.requestedScopes = [.fullName, .email]
        request.nonce = NonceGenerator.sha256(nonce)
    }

    @MainActor
    func onSignInCompletion(_ result: Result<ASAuthorization, Error>) async {
        guard
            case .success(let authorization) = result,
            let nonce = currentNonce
        else {
            if
                case .failure(let error) = result,
                (error as? ASAuthorizationError)?.code != .canceled
            {
                alertItem = AlertItem(title: L10n.Account.errorSignInFailed)
            }
            return
        }

        state = .linking
        mergeStatusReporting.beginMerge()
        do {
            try await signInWithApple(authorization: authorization, nonce: nonce)
        } catch {
            alertItem = AlertItem(title: L10n.Account.errorSignInFailed)
        }
        mergeStatusReporting.endMerge()
        state = .idle
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
