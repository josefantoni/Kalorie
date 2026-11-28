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
    private let makeCatalogueEditorView: (FoodItemReportDomain, @escaping () -> Void) -> ModerationCatalogueEditorView

    // MARK: - Init

    init(
        viewModel: ModerationReportsViewModel,
        makeCatalogueEditorView: @escaping (FoodItemReportDomain, @escaping () -> Void) -> ModerationCatalogueEditorView
    ) {
        self._viewModel = StateObject(wrappedValue: viewModel)
        self.makeCatalogueEditorView = makeCatalogueEditorView
    }

    // MARK: - Body

    var body: some View {
        List {
            if viewModel.rows.isEmpty && !viewModel.state.isLoading {
                Text(L10n.Moderation.reportsEmpty)
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            }
            ForEach(viewModel.rows) { row in
                NavigationLink {
                    makeCatalogueEditorView(row.report) {
                        viewModel.onReportResolved(id: row.id)
                    }
                } label: {
                    VStack(alignment: .leading) {
                        Text(row.itemName ?? row.report.barcode)
                        Text(row.report.reason)
                            .font(.caption)
                            .foregroundStyle(.secondary)
                            .lineLimit(2)
                    }
                }
            }
        }
        .appSectionSpacing()
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

#if DEBUG
// MARK: - Preview

#Preview {
    NavigationStack {
        ModerationReportsView(
            viewModel: ModerationReportsViewModel(
                fetchFoodItemReports: FetchFoodItemReportsUseCaseFake(),
                fetchFoodItemByBarcode: FetchFoodItemByBarcodeUseCaseFake()
            )
        ) { report, onResolved in
            ModerationCatalogueEditorView(
                viewModel: ModerationCatalogueEditorViewModel(
                    fetchFoodItemByBarcode: FetchFoodItemByBarcodeUseCaseFake(),
                    updateFoodItem: UpdateFoodItemUseCaseFake(),
                    resolveFoodItemReport: ResolveFoodItemReportUseCaseFake(),
                    modelExtractor: NutritionLabelModelExtractorFake(),
                    cameraAuthorizationProvider: CameraAuthorizationProviderFake(),
                    ticket: report,
                    onResolved: onResolved
                )
            )
        }
    }
}
#endif
