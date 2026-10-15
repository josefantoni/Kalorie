//
//  AccountConfigurator.swift
//  Kalorie
//
//  Created by Josef Antoni on 07.08.2026.
//

import Foundation

struct AccountConfigurator {

    // MARK: - Properties

    private let dataProvider: any FirestoreDataProviderProtocol
    private let authProvider: any AuthProviderProtocol
    private let mergeStatusReporting: any MergeStatusReporting

    // MARK: - Init

    init(
        dataProvider: any FirestoreDataProviderProtocol,
        authProvider: any AuthProviderProtocol,
        mergeStatusReporting: any MergeStatusReporting
    ) {
        self.dataProvider = dataProvider
        self.authProvider = authProvider
        self.mergeStatusReporting = mergeStatusReporting
    }

    // MARK: - Functions

    func createView() -> AccountView {
        let authCommandProvider = AuthCommandProvider()
        let snapshotStore = PendingMergeSnapshotStore()
        let migrateAnonymousData = MigrateAnonymousDataUseCase(
            dataProvider: dataProvider,
            authProvider: authProvider,
            authCommandProvider: authCommandProvider,
            snapshotStore: snapshotStore
        )
        let linkOrMergeCredential = LinkOrMergeCredentialUseCase(
            authProvider: authProvider,
            authCommandProvider: authCommandProvider,
            migrateAnonymousData: migrateAnonymousData
        )
        let linkCredential = LinkCredentialUseCase(
            authProvider: authProvider,
            authCommandProvider: authCommandProvider,
            snapshotStore: snapshotStore
        )
        let fillEmptyProfile = FillEmptyProfileUseCase(
            authProvider: authProvider,
            authCommandProvider: authCommandProvider,
            dataProvider: dataProvider
        )
        return AccountView(
            viewModel: AccountViewModel(
                authProvider: authProvider,
                signOut: makeSignOut(authCommandProvider: authCommandProvider, snapshotStore: snapshotStore),
                signInWithApple: SignInWithAppleUseCase(
                    appleSignInProvider: AppleSignInProvider(),
                    linkOrMergeCredential: linkOrMergeCredential,
                    fillEmptyProfile: fillEmptyProfile
                ),
                signInWithGoogle: SignInWithGoogleUseCase(
                    googleSignInProvider: GoogleSignInProvider(),
                    linkOrMergeCredential: linkOrMergeCredential,
                    fillEmptyProfile: fillEmptyProfile
                ),
                linkApple: LinkAppleUseCase(
                    appleSignInProvider: AppleSignInProvider(),
                    linkCredential: linkCredential,
                    fillEmptyProfile: fillEmptyProfile
                ),
                linkGoogle: LinkGoogleUseCase(
                    googleSignInProvider: GoogleSignInProvider(),
                    linkCredential: linkCredential,
                    fillEmptyProfile: fillEmptyProfile
                ),
                deleteAccount: makeDeleteAccount(authCommandProvider: authCommandProvider, snapshotStore: snapshotStore),
                reauthenticate: makeReauthenticate(authCommandProvider: authCommandProvider),
                mergeStatusReporting: mergeStatusReporting
            )
        )
    }

    private func makeSignOut(authCommandProvider: AuthCommandProvider, snapshotStore: PendingMergeSnapshotStore) -> SignOutUseCase {
        SignOutUseCase(
            authCommandProvider: authCommandProvider,
            snapshotStore: snapshotStore,
            googleSessionProvider: GoogleSessionProvider()
        )
    }

    private func makeDeleteAccount(authCommandProvider: AuthCommandProvider, snapshotStore: PendingMergeSnapshotStore) -> DeleteAccountUseCase {
        DeleteAccountUseCase(
            dataProvider: dataProvider,
            authProvider: authProvider,
            authCommandProvider: authCommandProvider,
            snapshotStore: snapshotStore,
            storageProvider: StorageDataProvider(),
            appleSignInProvider: AppleSignInProvider()
        )
    }

    private func makeReauthenticate(authCommandProvider: AuthCommandProvider) -> ReauthenticateUseCase {
        ReauthenticateUseCase(
            appleSignInProvider: AppleSignInProvider(),
            googleSignInProvider: GoogleSignInProvider(),
            authCommandProvider: authCommandProvider,
            authProvider: authProvider
        )
    }
}
