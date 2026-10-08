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
            },
            makeCatalogueEditorView: { [self] barcode in makeCatalogueEditorView(initialBarcode: barcode) }
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

    // MARK: - Private

    private func makeCatalogueEditorView(
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
