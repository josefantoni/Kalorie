//
//  NotificationsView.swift
//  Kalorie
//
//  Created by Josef Antoni on 07.11.2026.
//

import SwiftUI

struct NotificationsView: View {

    // MARK: - Properties

    @StateObject var viewModel: NotificationsViewModel
    private let makeSubmissionView: (FoodItemSubmissionDomain, @escaping () -> Void) -> ModerationReviewView
    private let makeReportView: (String, [FoodItemReportDomain]) -> ModerationCatalogueEditorView
    private let makePhotoSuggestionView: (PhotoSuggestionDomain, @escaping () -> Void) -> ModerationPhotoSuggestionView

    // MARK: - Init

    init(
        viewModel: NotificationsViewModel,
        makeSubmissionView: @escaping (FoodItemSubmissionDomain, @escaping () -> Void) -> ModerationReviewView,
        makeReportView: @escaping (String, [FoodItemReportDomain]) -> ModerationCatalogueEditorView,
        makePhotoSuggestionView: @escaping (PhotoSuggestionDomain, @escaping () -> Void) -> ModerationPhotoSuggestionView
    ) {
        self._viewModel = StateObject(wrappedValue: viewModel)
        self.makeSubmissionView = makeSubmissionView
        self.makeReportView = makeReportView
        self.makePhotoSuggestionView = makePhotoSuggestionView
    }

    // MARK: - Body

    var body: some View {
        NavigationStack {
            List {
                ForEach(viewModel.rows) { row in
                    NavigationLink {
                        destination(for: row)
                    } label: {
                        label(for: row)
                    }
                    .swipeActions {
                        if case .report = row.kind {
                            Button(L10n.Moderation.buttonResolve) {
                                Task { await viewModel.onResolveReportsTapped(row) }
                            }
                            .tint(Color.success)
                        }
                    }
                }
            }
            .scrollContentBackground(.hidden)
            .background(Color(.systemGroupedBackground))
            .overlay {
                if viewModel.rows.isEmpty && !viewModel.state.isLoading {
                    VStack(spacing: 12) {
                        Image(systemName: BaseImageName.bell.rawValue)
                            .font(.system(size: Constants.ImageSize.extraLarge))
                        Text(L10n.Notifications.empty)
                            .font(.title3)
                    }
                    .foregroundStyle(Color(uiColor: UIColor { $0.userInterfaceStyle == .dark ? .systemGray4 : .systemGray3 }))
                    .offset(y: -100)
                    .accessibilityElement(children: .combine)
                }
            }
            .navigationTitle(L10n.Notifications.title)
            .navigationBarTitleDisplayMode(.inline)
            .loader(viewModel.state.isLoading)
            .toolbar {
                DismissToolbarItem()
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

    // MARK: - Functions

    @ViewBuilder
    private func destination(for row: NotificationsViewModel.Row) -> some View {
        switch row.kind {
        case .submission(let submission):
            makeSubmissionView(submission) { viewModel.onRowResolved(id: row.id) }
        case .report(let barcode, let reports):
            makeReportView(barcode, reports)
        case .photoSuggestion(let suggestion):
            makePhotoSuggestionView(suggestion) { viewModel.onRowResolved(id: row.id) }
        }
    }

    private func label(for row: NotificationsViewModel.Row) -> some View {
        HStack(spacing: 12) {
            Image(systemName: iconName(for: row.kind))
                .foregroundStyle(.secondary)
                .frame(width: 24)
            VStack(alignment: .leading) {
                Text(row.title)
                Text(kindLabel(for: row.kind))
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
        }
    }

    private func iconName(for kind: NotificationsViewModel.Row.Kind) -> String {
        switch kind {
        case .submission: BaseImageName.plusCircle.rawValue
        case .report: BaseImageName.pencil.rawValue
        case .photoSuggestion: BaseImageName.cameraFill.rawValue
        }
    }

    private func kindLabel(for kind: NotificationsViewModel.Row.Kind) -> String {
        switch kind {
        case .submission: L10n.Notifications.kindSubmission
        case .report: L10n.Notifications.kindReport
        case .photoSuggestion: L10n.Notifications.kindPhoto
        }
    }
}

#if DEBUG
// MARK: - Preview

#Preview {
    NotificationsView(
        viewModel: NotificationsViewModel(
            fetchPendingSubmissions: FetchPendingSubmissionsUseCaseFake(),
            fetchFoodItemReports: FetchFoodItemReportsUseCaseFake(),
            fetchPhotoSuggestions: FetchPhotoSuggestionsUseCaseFake(),
            fetchFoodItemByBarcode: FetchFoodItemByBarcodeUseCaseFake()
        ),
        makeSubmissionView: { submission, onResolved in
            ModerationReviewView(
                viewModel: ModerationReviewViewModel(
                    submission: submission,
                    approveSubmission: ApproveSubmissionUseCaseFake(),
                    rejectSubmission: RejectSubmissionUseCaseFake(),
                    searchFoodItems: SearchFoodItemsUseCaseFake(),
                    modelExtractor: NutritionLabelModelExtractorFake(),
                    cameraAuthorizationProvider: CameraAuthorizationProviderFake(),
                    onResolved: onResolved
                )
            )
        },
        makeReportView: { _, _, _ in
            ModerationCatalogueEditorView(
                viewModel: ModerationCatalogueEditorViewModel(
                    fetchFoodItemByBarcode: FetchFoodItemByBarcodeUseCaseFake(),
                    updateFoodItem: UpdateFoodItemUseCaseFake(),
                    deleteFoodItemReport: DeleteFoodItemReportUseCaseFake(),
                    modelExtractor: NutritionLabelModelExtractorFake(),
                    cameraAuthorizationProvider: CameraAuthorizationProviderFake()
                )
            )
        },
        makePhotoSuggestionView: { suggestion, _ in
            ModerationPhotoSuggestionView(
                viewModel: ModerationPhotoSuggestionViewModel(
                    suggestion: suggestion,
                    fetchFoodItemByBarcode: FetchFoodItemByBarcodeUseCaseFake(),
                    approvePhotoSuggestion: ApprovePhotoSuggestionUseCaseFake(),
                    rejectPhotoSuggestion: RejectPhotoSuggestionUseCaseFake()
                ) { _ in }
            )
        }
    )
}
#endif
