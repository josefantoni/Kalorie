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
    private let maintainerClaimCache: MaintainerClaimCache
    private let moderationConfigurator: ModerationConfigurator

    // MARK: - Init

    init(
        dataProvider: any FirestoreDataProviderProtocol,
        authProvider: any AuthProviderProtocol,
        maintainerClaimCache: MaintainerClaimCache
    ) {
        self.dataProvider = dataProvider
        self.authProvider = authProvider
        self.maintainerClaimCache = maintainerClaimCache
        self.moderationConfigurator = ModerationConfigurator(dataProvider: dataProvider, authProvider: authProvider)
    }

    // MARK: - Functions

    func createBellViewModel() -> NotificationBellViewModel {
        NotificationBellViewModel(
            fetchMaintainerClaim: FetchMaintainerClaimUseCase(authProvider: authProvider, cache: maintainerClaimCache, forcesRefresh: false),
            fetchHasPendingNotifications: FetchHasPendingNotificationsUseCase(dataProvider: dataProvider)
        )
    }

    func createView() -> NotificationsView {
        NotificationsView(
            viewModel: NotificationsViewModel(
                fetchPendingSubmissions: FetchPendingSubmissionsUseCase(dataProvider: dataProvider, authProvider: authProvider),
                fetchFoodItemReports: FetchFoodItemReportsUseCase(dataProvider: dataProvider, authProvider: authProvider),
                fetchPhotoSuggestions: FetchPhotoSuggestionsUseCase(dataProvider: dataProvider, authProvider: authProvider),
                fetchFoodItemByBarcode: FetchFoodItemByBarcodeUseCase(dataProvider: dataProvider),
                deleteFoodItemReport: DeleteFoodItemReportUseCase(dataProvider: dataProvider, authProvider: authProvider)
            ),
            makeSubmissionView: { [moderationConfigurator] submission, onResolved in
                moderationConfigurator.makeReviewView(submission: submission, onResolved: onResolved)
            },
            makeReportView: { [moderationConfigurator] barcode, reports in
                moderationConfigurator.makeCatalogueEditorView(initialBarcode: barcode, reports: reports)
            },
            makePhotoSuggestionView: { [moderationConfigurator] suggestion, onResolved in
                moderationConfigurator.makePhotoSuggestionView(suggestion: suggestion, onResolved: onResolved)
            }
        )
    }
}
