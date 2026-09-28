//
//  AccountView.swift
//  Kalorie
//
//  Created by Josef Antoni on 07.08.2026.
//

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

                        HStack(spacing: 20) {
                            VStack(spacing: 6) {
                                Button {
                                    Task { await viewModel.onSignInWithAppleTapped() }
                                } label: {
                                    Image(systemName: "apple.logo")
                                        .font(.system(size: 22, weight: .medium))
                                        .foregroundStyle(Color(uiColor: .systemBackground))
                                        .frame(width: 56, height: 56)
                                        .background(Color.primary, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
                                }
                                .buttonStyle(.plain)
                                .accessibilityLabel(L10n.Account.buttonSignInWithApple)

                                Text(L10n.Account.signInProviderApple)
                                    .font(.caption2)
                                    .foregroundStyle(.secondary)
                            }

                            VStack(spacing: 6) {
                                Button {
                                    Task { await viewModel.onSignInWithGoogleTapped() }
                                } label: {
                                    Image(.googleLogo)
                                        .resizable()
                                        .scaledToFit()
                                        .frame(width: 22, height: 22)
                                        .frame(width: 56, height: 56)
                                        .background(Color(uiColor: .systemBackground), in: RoundedRectangle(cornerRadius: 16, style: .continuous))
                                        .overlay(
                                            RoundedRectangle(cornerRadius: 16, style: .continuous)
                                                .strokeBorder(Color(uiColor: .separator))
                                        )
                                }
                                .buttonStyle(.plain)
                                .accessibilityLabel(L10n.Account.buttonSignInWithGoogle)

                                Text(L10n.Account.signInProviderGoogle)
                                    .font(.caption2)
                                    .foregroundStyle(.secondary)
                            }
                        }
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 20)
                        .listRowInsets(EdgeInsets())
                        .listRowBackground(Color(uiColor: .secondarySystemGroupedBackground))
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
            .safeAreaInset(edge: .bottom) {
                VStack(alignment: .leading, spacing: 4) {
                    Text(L10n.Account.dataAttribution)
                        .foregroundStyle(.secondary)

                    if let openFoodFactsURL = Constants.OpenFoodFacts.baseURL {
                        Link(L10n.Account.dataAttributionLinkTitle, destination: openFoodFactsURL)
                            .frame(maxWidth: .infinity, alignment: .center)
                    }
                }
                .font(.caption)
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.horizontal, 16)
                .padding(.vertical, 10)
                .background(.bar)
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
                    message: item.message.map(Text.init),
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
            .alert(
                L10n.Account.alertReauthenticateTitle,
                isPresented: $viewModel.isReauthenticateAlertVisible
            ) {
                Button(L10n.Common.buttonCancel, role: .cancel) {}
                Button(L10n.Account.buttonReauthenticate) {
                    Task { await viewModel.onReauthenticateConfirmed() }
                }
            } message: {
                Text(L10n.Account.errorDeleteRequiresRecentLogin)
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
            signInWithGoogle: SignInWithGoogleUseCaseFake(),
            deleteAccount: DeleteAccountUseCaseFake(),
            reauthenticate: ReauthenticateUseCaseFake(),
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
            signInWithGoogle: SignInWithGoogleUseCaseFake(),
            deleteAccount: DeleteAccountUseCaseFake(),
            reauthenticate: ReauthenticateUseCaseFake(),
            mergeStatusReporting: MergeStatusReportingFake()
        )
    )
}
