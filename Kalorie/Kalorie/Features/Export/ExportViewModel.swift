//
//  ExportViewModel.swift
//  Kalorie
//
//  Created by Josef Antoni on 19.09.2026.
//

import Foundation

struct ExportedFile: Identifiable {
    let url: URL
    var id: URL { url }
}

final class ExportViewModel: ObservableObject {

    enum State {
        case idle
        case generating
    }

    // MARK: - Properties

    @Published private(set) var state: State = .idle
    @Published var fromDate: Date
    @Published var toDate: Date
    @Published var format: FoodExportFormat = .pdf
    @Published var exportedFile: ExportedFile?
    @Published var alertItem: AlertItem?

    private let mealTypes: [MealTypeDomain]
    private let generateFoodExport: any GenerateFoodExportUseCaseProtocol

    var isExportDisabled: Bool {
        state == .generating || Calendar.current.startOfDay(for: fromDate) > Calendar.current.startOfDay(for: toDate)
    }

    // MARK: - Init

    init(
        mealTypes: [MealTypeDomain],
        generateFoodExport: any GenerateFoodExportUseCaseProtocol,
        now: Date = .now
    ) {
        let calendar = Calendar.current
        self.mealTypes = mealTypes
        self.generateFoodExport = generateFoodExport
        self.fromDate = calendar.date(from: calendar.dateComponents([.year, .month], from: now)) ?? now
        self.toDate = now
    }

    // MARK: - Functions

    @MainActor
    func onExportTapped() async {
        guard !isExportDisabled else { return }
        state = .generating
        defer { state = .idle }
        do {
            let url = try await generateFoodExport(from: fromDate, to: toDate, format: format, mealTypes: mealTypes)
            exportedFile = ExportedFile(url: url)
        } catch {
            Log.error(error, category: Constants.LogCategory.export)
            alertItem = AlertItem(title: L10n.Common.errorUnknown)
        }
    }

    @MainActor
    func onShareFinished() {
        if let url = exportedFile?.url {
            try? FileManager.default.removeItem(at: url)
        }
        exportedFile = nil
    }
}
