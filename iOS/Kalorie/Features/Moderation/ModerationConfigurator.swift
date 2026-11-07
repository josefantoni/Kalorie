//
//  ModerationConfigurator.swift
//  Kalorie
//
//  Created by Josef Antoni on 10.09.2026.
//

import Foundation

struct ModerationConfigurator {

    // MARK: - Properties

    private let dataProvider: any FirestoreDataProviderProtocol
    private let authProvider: any AuthProviderProtocol
    private let storageProvider: any StorageDataProviderProtocol

    // MARK: - Init

    init(
        dataProvider: any FirestoreDataProviderProtocol,
        authProvider: any AuthProviderProtocol,
        storageProvider: any StorageDataProviderProtocol = StorageDataProvider()
    ) {
        self.dataProvider = dataProvider
        self.authProvider = authProvider
        self.storageProvider = storageProvider
    }

    // MARK: - Functions

    func createView() -> ModerationQueueView {
        ModerationQueueView(
            viewModel: ModerationQueueViewModel(
                fetchPendingSubmissions: FetchPendingSubmissionsUseCase(dataProvider: dataProvider, authProvider: authProvider),
                fetchFoodItemByBarcode: FetchFoodItemByBarcodeUseCase(dataProvider: dataProvider)
            ),
            makeReviewView: { [self] submission, onResolved in
                makeReviewView(submission: submission, onResolved: onResolved)
            },
            makeCatalogueEditorView: { [self] barcode in makeCatalogueEditorView(initialBarcode: barcode) }
        )
    }

    func makeReviewView(submission: FoodItemSubmissionDomain, onResolved: @escaping () -> Void) -> ModerationReviewView {
        ModerationReviewView(
            viewModel: ModerationReviewViewModel(
                submission: submission,
                approveSubmission: ApproveSubmissionUseCase(
                    dataProvider: dataProvider,
                    authProvider: authProvider,
                    createFoodItem: CreateFoodItemUseCase(dataProvider: dataProvider),
                    downloadFoodPhoto: DownloadFoodPhotoUseCase(storageProvider: storageProvider),
                    uploadFoodPhoto: UploadFoodPhotoUseCase(storageProvider: storageProvider),
                    deleteFoodPhoto: DeleteFoodPhotoUseCase(storageProvider: storageProvider)
                ),
                rejectSubmission: RejectSubmissionUseCase(dataProvider: dataProvider, authProvider: authProvider),
                searchFoodItems: SearchFoodItemsUseCase(dataProvider: dataProvider),
                modelExtractor: FoundationModelExtractor(),
                cameraAuthorizationProvider: CameraAuthorizationProvider(),
                onResolved: onResolved
            )
        )
    }

    func createReportsView() -> ModerationReportsView {
        ModerationReportsView(
            viewModel: ModerationReportsViewModel(
                fetchFoodItemReports: FetchFoodItemReportsUseCase(dataProvider: dataProvider, authProvider: authProvider),
                fetchFoodItemByBarcode: FetchFoodItemByBarcodeUseCase(dataProvider: dataProvider),
                deleteFoodItemReport: DeleteFoodItemReportUseCase(dataProvider: dataProvider, authProvider: authProvider)
            )
        ) { [self] barcode, reports in
            makeCatalogueEditorView(initialBarcode: barcode, reports: reports)
        }
    }

    func createPhotoSuggestionsView() -> ModerationPhotoSuggestionsView {
        ModerationPhotoSuggestionsView(
            viewModel: ModerationPhotoSuggestionsViewModel(
                fetchPhotoSuggestions: FetchPhotoSuggestionsUseCase(dataProvider: dataProvider, authProvider: authProvider),
                fetchFoodItemByBarcode: FetchFoodItemByBarcodeUseCase(dataProvider: dataProvider)
            )
        ) { [self] suggestion, onResolved in
            makePhotoSuggestionView(suggestion: suggestion, onResolved: onResolved)
        }
    }

    func makePhotoSuggestionView(suggestion: PhotoSuggestionDomain, onResolved: @escaping () -> Void) -> ModerationPhotoSuggestionView {
        ModerationPhotoSuggestionView(
            viewModel: ModerationPhotoSuggestionViewModel(
                suggestion: suggestion,
                fetchFoodItemByBarcode: FetchFoodItemByBarcodeUseCase(dataProvider: dataProvider),
                approvePhotoSuggestion: ApprovePhotoSuggestionUseCase(
                    dataProvider: dataProvider,
                    authProvider: authProvider,
                    storageProvider: storageProvider,
                    fetchFoodItemByBarcode: FetchFoodItemByBarcodeUseCase(dataProvider: dataProvider),
                    downloadFoodPhoto: DownloadFoodPhotoUseCase(storageProvider: storageProvider),
                    updateFoodItem: UpdateFoodItemUseCase(
                        dataProvider: dataProvider,
                        authProvider: authProvider,
                        uploadFoodPhoto: UploadFoodPhotoUseCase(storageProvider: storageProvider),
                        deleteFoodPhoto: DeleteFoodPhotoUseCase(storageProvider: storageProvider)
                    )
                ),
                rejectPhotoSuggestion: RejectPhotoSuggestionUseCase(
                    dataProvider: dataProvider,
                    authProvider: authProvider,
                    storageProvider: storageProvider
                ),
                onResolved: onResolved
            )
        )
    }

    // MARK: - Private

    func makeCatalogueEditorView(
        initialBarcode: String?,
        reports: [FoodItemReportDomain] = []
    ) -> ModerationCatalogueEditorView {
        ModerationCatalogueEditorView(
            viewModel: ModerationCatalogueEditorViewModel(
                fetchFoodItemByBarcode: FetchFoodItemByBarcodeUseCase(dataProvider: dataProvider),
                updateFoodItem: UpdateFoodItemUseCase(
                    dataProvider: dataProvider,
                    authProvider: authProvider,
                    uploadFoodPhoto: UploadFoodPhotoUseCase(storageProvider: storageProvider),
                    deleteFoodPhoto: DeleteFoodPhotoUseCase(storageProvider: storageProvider)
                ),
                modelExtractor: FoundationModelExtractor(),
                cameraAuthorizationProvider: CameraAuthorizationProvider(),
                initialBarcode: initialBarcode
            ),
            reports: reports
        )
    }
}
