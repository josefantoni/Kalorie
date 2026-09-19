//
//  ExportView.swift
//  Kalorie
//
//  Created by Josef Antoni on 19.09.2026.
//

import SwiftUI

struct ExportView: View {

    // MARK: - Properties

    @StateObject var viewModel: ExportViewModel

    // MARK: - Init

    init(viewModel: ExportViewModel) {
        _viewModel = StateObject(wrappedValue: viewModel)
    }

    // MARK: - Body

    var body: some View {
        Form {
            Section {
                DatePicker(L10n.Export.datePickerFrom, selection: $viewModel.fromDate, displayedComponents: .date)
                DatePicker(L10n.Export.datePickerTo, selection: $viewModel.toDate, displayedComponents: .date)
            }
            Section {
                Picker(L10n.Export.pickerFormat, selection: $viewModel.format) {
                    Text(L10n.Export.formatPdf).tag(FoodExportFormat.pdf)
                    Text(L10n.Export.formatExcel).tag(FoodExportFormat.xlsx)
                }
                .pickerStyle(.segmented)
            }
            Section {
                Button {
                    Task { await viewModel.onExportTapped() }
                } label: {
                    Text(L10n.Export.buttonExport)
                        .frame(maxWidth: .infinity)
                }
                .disabled(viewModel.isExportDisabled)
            }
        }
        .navigationTitle(L10n.Export.navigationTitle)
        .navigationBarTitleDisplayMode(.inline)
        .loader(viewModel.state == .generating)
        .sheet(item: $viewModel.exportedFile) { file in
            ActivityView(url: file.url) {
                viewModel.onShareFinished()
            }
        }
        .alert(item: $viewModel.alertItem) { item in
            Alert(
                title: Text(item.title),
                message: item.message.map(Text.init),
                dismissButton: Alert.Button.default(Text(L10n.Common.ok))
            )
        }
    }
}

// MARK: - Preview

#Preview {
    NavigationStack {
        ExportView(viewModel: ExportViewModel(mealTypes: [], generateFoodExport: GenerateFoodExportUseCaseFake()))
    }
}
