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
    private let makeCatalogueEditorView: (String, [FoodItemReportDomain], @escaping ([FoodItemReportDomain]) -> Void) -> ModerationCatalogueEditorView

    // MARK: - Init

    init(
        viewModel: ModerationReportsViewModel,
        makeCatalogueEditorView: @escaping (String, [FoodItemReportDomain], @escaping ([FoodItemReportDomain]) -> Void) -> ModerationCatalogueEditorView
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
                    makeCatalogueEditorView(group.barcode, group.reports) { remaining in
                        viewModel.onGroupReportsChanged(barcode: group.barcode, remaining: remaining)
                    }
                } label: {
                    VStack(alignment: .leading) {
                        Text(group.itemName ?? group.barcode)
                        Text(L10n.Moderation.reportsCount(group.reports.count))
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
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

#if DEBUG
// MARK: - Preview

#Preview {
    NavigationStack {
        ModerationReportsView(
            viewModel: ModerationReportsViewModel(
                fetchFoodItemReports: FetchFoodItemReportsUseCaseFake(),
                fetchFoodItemByBarcode: FetchFoodItemByBarcodeUseCaseFake()
            )
        ) { barcode, reports, onReportsChanged in
            ModerationCatalogueEditorView(
                viewModel: ModerationCatalogueEditorViewModel(
                    fetchFoodItemByBarcode: FetchFoodItemByBarcodeUseCaseFake(),
                    updateFoodItem: UpdateFoodItemUseCaseFake(),
                    deleteFoodItemReport: DeleteFoodItemReportUseCaseFake(),
                    modelExtractor: NutritionLabelModelExtractorFake(),
                    cameraAuthorizationProvider: CameraAuthorizationProviderFake(),
                    initialBarcode: barcode,
                    reports: reports,
                    onReportsChanged: onReportsChanged
                )
            )
        }
    }
}
#endif
