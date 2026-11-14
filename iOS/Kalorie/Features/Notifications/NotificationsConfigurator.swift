//
//  NotificationsConfigurator.swift
//  Kalorie
//
//  Created by Josef Antoni on 07.11.2026.
//

struct NotificationsConfigurator {

    // MARK: - Properties

    private let dataProvider: any FirestoreDataProviderProtocol
    private let authProvider: any AuthProviderProtocol
    private let storageProvider: any StorageDataProviderProtocol
    private let maintainerClaimCache: MaintainerClaimCache
    private let moderationConfigurator: ModerationConfigurator
    private let notificationsStore: any UserNotificationsStoreProtocol

    // MARK: - Init

    init(
        dataProvider: any FirestoreDataProviderProtocol,
        authProvider: any AuthProviderProtocol,
        maintainerClaimCache: MaintainerClaimCache,
        storageProvider: any StorageDataProviderProtocol = StorageDataProvider()
    ) {
        self.dataProvider = dataProvider
        self.authProvider = authProvider
        self.storageProvider = storageProvider
        self.maintainerClaimCache = maintainerClaimCache
        self.moderationConfigurator = ModerationConfigurator(dataProvider: dataProvider, authProvider: authProvider)
        self.notificationsStore = UserNotificationsStore(authProvider: authProvider)
    }

    // MARK: - Functions

    func createBellViewModel() -> NotificationBellViewModel {
        NotificationBellViewModel(
            fetchMaintainerClaim: FetchMaintainerClaimUseCase(authProvider: authProvider, cache: maintainerClaimCache, forcesRefresh: false),
            fetchHasPendingNotifications: FetchHasPendingNotificationsUseCase(dataProvider: dataProvider),
            fetchUserNotifications: FetchUserNotificationsUseCase(dataProvider: dataProvider, authProvider: authProvider),
            notificationsStore: notificationsStore
        )
    }

    func createView(isMaintainer: Bool) -> NotificationsView {
        NotificationsView(
            viewModel: NotificationsViewModel(
                fetchPendingSubmissions: FetchPendingSubmissionsUseCase(dataProvider: dataProvider, authProvider: authProvider),
                fetchFoodItemReports: FetchFoodItemReportsUseCase(dataProvider: dataProvider, authProvider: authProvider),
                fetchPhotoSuggestions: FetchPhotoSuggestionsUseCase(dataProvider: dataProvider, authProvider: authProvider),
                fetchFoodItemByBarcode: FetchFoodItemByBarcodeUseCase(dataProvider: dataProvider),
                isMaintainer: isMaintainer,
                fetchUserNotifications: FetchUserNotificationsUseCase(dataProvider: dataProvider, authProvider: authProvider),
                markUserNotificationSeen: MarkUserNotificationSeenUseCase(dataProvider: dataProvider, authProvider: authProvider),
                hideUserNotification: HideUserNotificationUseCase(dataProvider: dataProvider, authProvider: authProvider),
                withdrawUserNotification: WithdrawUserNotificationUseCase(
                    dataProvider: dataProvider,
                    authProvider: authProvider,
                    storageProvider: storageProvider
                ),
                notificationsStore: notificationsStore
            ),
            makeSubmissionView: { [moderationConfigurator] submission, onResolved in
                moderationConfigurator.makeReviewView(submission: submission, onResolved: onResolved)
            },
            makeReportView: { [moderationConfigurator] report, onResolved in
                moderationConfigurator.makeCatalogueEditorView(ticket: report, onResolved: onResolved)
            },
            makePhotoSuggestionView: { [moderationConfigurator] suggestion, onResolved in
                moderationConfigurator.makePhotoSuggestionView(suggestion: suggestion, onResolved: onResolved)
            }
        )
    }
}
