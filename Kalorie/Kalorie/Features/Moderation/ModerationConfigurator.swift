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

    // MARK: - Init

    init(dataProvider: any FirestoreDataProviderProtocol, authProvider: any AuthProviderProtocol) {
        self.dataProvider = dataProvider
        self.authProvider = authProvider
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
                            createFoodItem: CreateFoodItemUseCase(dataProvider: dataProvider)
                        ),
                        rejectSubmission: RejectSubmissionUseCase(dataProvider: dataProvider, authProvider: authProvider),
                        onResolved: onResolved
                    )
                )
            },
            makeCatalogueEditorView: { [self] in
                ModerationCatalogueEditorView(
                    viewModel: ModerationCatalogueEditorViewModel(
                        fetchFoodItemByBarcode: FetchFoodItemByBarcodeUseCase(dataProvider: dataProvider),
                        updateFoodItem: UpdateFoodItemUseCase(dataProvider: dataProvider, authProvider: authProvider)
                    )
                )
            }
        )
    }
}
