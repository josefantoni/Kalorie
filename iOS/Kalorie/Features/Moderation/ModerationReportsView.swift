//
//  ModerationReportsView.swift
//  Kalorie
//
//  Created by Josef Antoni on 17.09.2026.
//

import SwiftUI

struct ModerationReportsView: View {

    // MARK: - Properties

    @StateObject var viewModel: ModerationReportsViewModel
    private let makeCatalogueEditorView: (String?) -> ModerationCatalogueEditorView

    // MARK: - Init

    init(
        viewModel: ModerationReportsViewModel,
        makeCatalogueEditorView: @escaping (String?) -> ModerationCatalogueEditorView
    ) {
        self._viewModel = StateObject(wrappedValue: viewModel)
        self.makeCatalogueEditorView = makeCatalogueEditorView
    }

    // MARK: - Body

    var body: some View {
        List {
            if viewModel.groups.isEmpty && !viewModel.state.isLoading {
                Text(L10n.Moderation.reportsEmpty)
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            }
            ForEach(viewModel.groups) { group in
                NavigationLink {
                    makeCatalogueEditorView(group.barcode)
                } label: {
                    VStack(alignment: .leading) {
                        Text(group.itemName ?? group.barcode)
                        Text(L10n.Moderation.reportsCount(group.reports.count))
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                }
                .swipeActions {
                    Button(L10n.Moderation.buttonResolve) {
                        Task { await viewModel.onResolveTapped(group) }
                    }
                    .tint(.green)
                }
            }
        }
        .navigationTitle(L10n.Moderation.reportsTitle)
        .navigationBarTitleDisplayMode(.inline)
        .loader(viewModel.state.isLoading)
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
        ModerationReportsView(
            viewModel: ModerationReportsViewModel(
                fetchFoodItemReports: FetchFoodItemReportsUseCaseFake(),
                fetchFoodItemByBarcode: FetchFoodItemByBarcodeUseCaseFake(),
                deleteFoodItemReport: DeleteFoodItemReportUseCaseFake()
            )
        ) { barcode in
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
    }
}
