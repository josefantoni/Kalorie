//
//  AccountView.swift
//  Kalorie
//
//  Created by Josef Antoni on 07.08.2026.
//

import AuthenticationServices
import SwiftUI

struct AccountView: View {

    // MARK: - Properties

    @StateObject var viewModel: AccountViewModel

    // MARK: - Init

    init(viewModel: AccountViewModel) {
        self._viewModel = StateObject(wrappedValue: viewModel)
    }

    // MARK: - Body

    var body: some View {
        NavigationStack {
            List {
                Section {
                    if viewModel.isAnonymous {
                        Text(L10n.Account.anonymousDescription)
                            .font(.subheadline)
                            .foregroundStyle(.secondary)

                        SignInWithAppleButton(.signIn) { request in
                            viewModel.onSignInRequest(request)
                        } onCompletion: { result in
                            Task { await viewModel.onSignInCompletion(result) }
                        }
                        .frame(height: 44)
                        .listRowInsets(EdgeInsets())
                        .listRowBackground(Color.clear)
                    } else {
                        Text(viewModel.displayName ?? L10n.Account.signedInDefaultName)
                            .font(.headline)

                        Button(L10n.Account.buttonSignOut, role: .destructive) {
                            viewModel.onSignOutTapped()
                        }

                        Button(L10n.Account.buttonDeleteAccount, role: .destructive) {
                            viewModel.showDeleteConfirmation = true
                        }
                    }
                }
            }
            .navigationTitle(L10n.Account.navigationTitle)
            .navigationBarTitleDisplayMode(.inline)
            .loader(viewModel.state != .idle)
            .toolbar {
                DismissToolbarItem()
            }
            .alert(item: $viewModel.alertItem) { item in
                Alert(
                    title: Text(item.title),
                    dismissButton: .default(Text(L10n.Common.ok))
                )
            }
            .confirmationDialog(
                L10n.Account.alertDeleteConfirmTitle,
                isPresented: $viewModel.showDeleteConfirmation,
                titleVisibility: .visible
            ) {
                Button(L10n.Account.buttonDeleteAccount, role: .destructive) {
                    Task { await viewModel.onDeleteAccountConfirmed() }
                }
            } message: {
                Text(L10n.Account.alertDeleteConfirmMessage)
            }
        }
    }
}

extension AccountViewModel.State: Equatable {}

// MARK: - Preview

#Preview {
    AccountView(
        viewModel: AccountViewModel(
            authProvider: AuthProviderFake(isAnonymous: true),
            signOut: SignOutUseCaseFake(),
            signInWithApple: SignInWithAppleUseCaseFake(),
            deleteAccount: DeleteAccountUseCaseFake(),
            mergeStatusReporting: MergeStatusReportingFake()
        )
    )
}

#Preview {
    AccountView(
        viewModel: AccountViewModel(
            authProvider: AuthProviderFake(isAnonymous: false, displayName: "Josef Antoni"),
            signOut: SignOutUseCaseFake(),
            signInWithApple: SignInWithAppleUseCaseFake(),
            deleteAccount: DeleteAccountUseCaseFake(),
            mergeStatusReporting: MergeStatusReportingFake()
        )
    )
}
