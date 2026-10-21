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
    @Environment(\.openURL) private var openURL

    // MARK: - Init

    init(viewModel: AccountViewModel) {
        self._viewModel = StateObject(wrappedValue: viewModel)
    }

    // MARK: - Body

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
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
                                        providerLogo(kind: .apple)
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
                                        providerLogo(kind: .google)
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
                            HStack {
                                Text(viewModel.displayName ?? L10n.Account.signedInDefaultName)
                                    .font(.headline)

                                Spacer()

                                BadgeButton(imageName: .infoCircle) {
                                    viewModel.isLinkInfoPopoverVisible = true
                                }
                                .popover(isPresented: $viewModel.isLinkInfoPopoverVisible) {
                                    linkInfoPopover
                                        .dynamicTypeLimit()
                                        .presentationCompactAdaptation(.popover)
                                }
                            }
                        }
                    }

                    Section {
                        if let privacyPolicyURL = Constants.Support.privacyPolicyURL {
                            linkRow(L10n.Common.privacyPolicy, url: privacyPolicyURL)
                        }

                        if let openFoodFactsURL = Constants.OpenFoodFacts.baseURL {
                            linkRow(L10n.Account.dataAttribution, url: openFoodFactsURL, isDescription: true)
                        }

                        if let ciqualURL = Constants.Ciqual.datasetURL {
                            linkRow(L10n.Account.ciqualAttribution, url: ciqualURL, isDescription: true)
                        }
                    }

                    if !viewModel.isAnonymous {
                        Section {
                            signOutButton
                                .listRowInsets(EdgeInsets())
                                .listRowBackground(Color.clear)
                        }
                    }
                }
                .listSectionSpacing(.custom(24))

                if !viewModel.isAnonymous {
                    PrimaryButton(L10n.Account.buttonDeleteAccount, style: .destructive) {
                        viewModel.showDeleteConfirmation = true
                    }
                    .padding(.horizontal, 16)
                    .padding(.vertical, 12)
                    .frame(maxWidth: .infinity)
                    .background(Color(uiColor: .systemGroupedBackground))
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
                    message: item.message.map(Text.init),
                    dismissButton: .default(Text(L10n.Common.ok))
                )
            }
            .alert(
                L10n.Account.alertSignOutConfirmTitle,
                isPresented: $viewModel.showSignOutConfirmation
            ) {
                Button(L10n.Common.buttonCancel, role: .cancel) {}
                Button(L10n.Account.buttonSignOut, role: .destructive) {
                    viewModel.onSignOutTapped()
                }
            }
            .alert(
                L10n.Account.alertDeleteConfirmTitle,
                isPresented: $viewModel.showDeleteConfirmation
            ) {
                Button(L10n.Common.buttonCancel, role: .cancel) {}
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

private extension AccountView {
    func linkRow(_ title: String, url: URL, isDescription: Bool = false) -> some View {
        Button {
            openURL(url)
        } label: {
            HStack {
                Text(title)
                    .font(isDescription ? .footnote : .body)
                    .multilineTextAlignment(.leading)
                Spacer()
                Image(systemName: BaseImageName.chevronRight.rawValue)
                    .font(.footnote.weight(.semibold))
                    .foregroundStyle(.tertiary)
            }
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }

    var signOutButton: some View {
        Button {
            viewModel.showSignOutConfirmation = true
        } label: {
            Text(L10n.Account.buttonSignOut)
                .font(.body)
                .fontWeight(.bold)
                .foregroundStyle(Color(.label))
                .frame(maxWidth: .infinity, minHeight: 56)
                .contentShape(.capsule)
        }
        .buttonStyle(.plain)
        .glassEffect(.regular.interactive(), in: .capsule)
    }

    var linkInfoPopover: some View {
        VStack(spacing: 16) {
            if let linkInfoText = viewModel.linkInfoText {
                Text(linkInfoText)
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                    .fixedSize(horizontal: false, vertical: true)
            }

            HStack(spacing: 28) {
                linkTile(kind: .apple)
                linkTile(kind: .google)
            }
        }
        .frame(width: 280)
        .padding()
    }

    func linkTile(kind: AuthProviderKind) -> some View {
        let isLinked = viewModel.linkedProviderKinds.contains(kind)
        return Button {
            Task { await viewModel.onLinkProviderTapped(kind) }
        } label: {
            providerLogo(kind: kind)
                .overlay(alignment: .bottomTrailing) {
                    if isLinked {
                        Image(systemName: BaseImageName.checkmark.rawValue)
                            .font(.system(size: 10, weight: .bold))
                            .foregroundStyle(.white)
                            .frame(width: 18, height: 18)
                            .background(Circle().fill(Color.success))
                            .overlay(Circle().strokeBorder(Color(uiColor: .secondarySystemGroupedBackground), lineWidth: 2))
                            .offset(x: 6, y: 6)
                    }
                }
                .overlay(alignment: .bottom) {
                    if !isLinked {
                        Text(L10n.Account.pillLink)
                            .font(.caption2.weight(.semibold))
                            .foregroundStyle(.white)
                            .lineLimit(1)
                            .minimumScaleFactor(0.5)
                            .padding(.horizontal, 6)
                            .padding(.vertical, 3)
                            .background(Capsule().fill(Color.accentColor))
                            .offset(y: 10)
                    }
                }
                .padding(.bottom, isLinked ? 0 : 10)
        }
        .buttonStyle(.plain)
        .allowsHitTesting(!isLinked)
        .accessibilityLabel(kind == .apple ? L10n.Account.buttonLinkWithApple : L10n.Account.buttonLinkWithGoogle)
        .accessibilityAddTraits(isLinked ? .isSelected : [])
    }

    @ViewBuilder
    func providerLogo(kind: AuthProviderKind) -> some View {
        switch kind {
        case .apple:
            Image(.appleLogo)
                .resizable()
                .scaledToFit()
                .frame(width: 22, height: 27)
                .frame(width: 56, height: 56)
                .background(Color.appleTile, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
        case .google:
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
    }
}

extension AccountViewModel.State: Equatable {}

#if DEBUG
// MARK: - Preview

#Preview {
    AccountView(
        viewModel: AccountViewModel(
            authProvider: AuthProviderFake(isAnonymous: true),
            signOut: SignOutUseCaseFake(),
            signInWithApple: SignInWithAppleUseCaseFake(),
            signInWithGoogle: SignInWithGoogleUseCaseFake(),
            linkApple: LinkAppleUseCaseFake(),
            linkGoogle: LinkGoogleUseCaseFake(),
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
            linkApple: LinkAppleUseCaseFake(),
            linkGoogle: LinkGoogleUseCaseFake(),
            deleteAccount: DeleteAccountUseCaseFake(),
            reauthenticate: ReauthenticateUseCaseFake(),
            mergeStatusReporting: MergeStatusReportingFake()
        )
    )
}
#endif
