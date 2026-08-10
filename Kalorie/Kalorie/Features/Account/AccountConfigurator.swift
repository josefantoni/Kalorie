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
        return AccountView(
            viewModel: AccountViewModel(
                authProvider: authProvider,
                signOut: SignOutUseCase(
                    authCommandProvider: authCommandProvider,
                    snapshotStore: snapshotStore,
                    googleSessionProvider: GoogleSessionProvider()
                ),
                signInWithApple: SignInWithAppleUseCase(
                    linkOrMergeCredential: linkOrMergeCredential,
                    authCommandProvider: authCommandProvider,
                    dataProvider: dataProvider,
                    authProvider: authProvider
                ),
                signInWithGoogle: SignInWithGoogleUseCase(
                    googleSignInProvider: GoogleSignInProvider(),
                    linkOrMergeCredential: linkOrMergeCredential,
                    authCommandProvider: authCommandProvider,
                    dataProvider: dataProvider,
                    authProvider: authProvider
                ),
                deleteAccount: DeleteAccountUseCase(
                    dataProvider: dataProvider,
                    authProvider: authProvider,
                    authCommandProvider: authCommandProvider
                ),
                mergeStatusReporting: mergeStatusReporting
            )
        )
    }
}
