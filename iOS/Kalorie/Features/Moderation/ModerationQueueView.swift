//
//  ModerationQueueView.swift
//  Kalorie
//
//  Created by Josef Antoni on 10.09.2026.
//

import SwiftUI

struct ModerationQueueView: View {

    // MARK: - Properties

    @StateObject var viewModel: ModerationQueueViewModel
    private let makeReviewView: (FoodItemSubmissionDomain, @escaping () -> Void) -> ModerationReviewView
    private let makeCatalogueEditorView: (String?) -> ModerationCatalogueEditorView

    // MARK: - Init

    init(
        viewModel: ModerationQueueViewModel,
        makeReviewView: @escaping (FoodItemSubmissionDomain, @escaping () -> Void) -> ModerationReviewView,
        makeCatalogueEditorView: @escaping (String?) -> ModerationCatalogueEditorView
    ) {
        self._viewModel = StateObject(wrappedValue: viewModel)
        self.makeReviewView = makeReviewView
        self.makeCatalogueEditorView = makeCatalogueEditorView
    }

    // MARK: - Body

    var body: some View {
        List {
            if viewModel.submissions.isEmpty && !viewModel.state.isLoading {
                Text(L10n.Moderation.queueEmpty)
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            }
            ForEach(viewModel.submissions, id: \.id) { submission in
                NavigationLink {
                    makeReviewView(submission) {
                        Task { await viewModel.onSubmissionResolved(id: submission.id) }
                    }
                } label: {
                    VStack(alignment: .leading) {
                        Text(submission.item.displayName)
                        if viewModel.isColliding(submission) {
                            Text(L10n.Moderation.queueCollision)
                                .font(.caption)
                                .foregroundStyle(.orange)
                        }
                    }
                }
            }
        }
        .navigationTitle(L10n.Moderation.queueTitle)
        .navigationBarTitleDisplayMode(.inline)
        .loader(viewModel.state.isLoading)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                NavigationLink {
                    makeCatalogueEditorView(nil)
                } label: {
                    Image(systemName: "pencil")
                }
            }
        }
        .alert(item: $viewModel.alertItem) { item in
            Alert(
                title: Text(item.title),
                dismissButton: .default(Text(L10n.Common.ok))
            )
        }
        .task { await viewModel.onAppear() }
        .refreshable { await viewModel.onRefresh() }
    }
}

// MARK: - Preview

#Preview {
    NavigationStack {
        ModerationQueueView(
            viewModel: ModerationQueueViewModel(
                fetchPendingSubmissions: FetchPendingSubmissionsUseCaseFake(),
                fetchFoodItemByBarcode: FetchFoodItemByBarcodeUseCaseFake()
            ),
            makeReviewView: { submission, onResolved in
                ModerationReviewView(
                    viewModel: ModerationReviewViewModel(
                        submission: submission,
                        approveSubmission: ApproveSubmissionUseCaseFake(),
                        rejectSubmission: RejectSubmissionUseCaseFake(),
                        searchFoodItems: SearchFoodItemsUseCaseFake(),
                        recognizeNutritionLabel: RecognizeNutritionLabelUseCaseFake(),
                        cameraAuthorizationProvider: CameraAuthorizationProviderFake(),
                        onResolved: onResolved
                    )
                )
            },
            makeCatalogueEditorView: { barcode in
                ModerationCatalogueEditorView(
                    viewModel: ModerationCatalogueEditorViewModel(
                        fetchFoodItemByBarcode: FetchFoodItemByBarcodeUseCaseFake(),
                        updateFoodItem: UpdateFoodItemUseCaseFake(),
                        recognizeNutritionLabel: RecognizeNutritionLabelUseCaseFake(),
                        cameraAuthorizationProvider: CameraAuthorizationProviderFake(),
                        initialBarcode: barcode
                    )
                )
            }
        )
    }
}
