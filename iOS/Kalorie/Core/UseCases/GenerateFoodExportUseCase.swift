//
//  GenerateFoodExportUseCase.swift
//  Kalorie
//
//  Created by Josef Antoni on 19.09.2026.
//

import ExportKit
import Foundation

protocol GenerateFoodExportUseCaseProtocol {
    func callAsFunction(from: Date, to: Date, format: FoodExportFormat, mealTypes: [MealTypeDomain]) async throws -> URL
}

struct GenerateFoodExportUseCase: GenerateFoodExportUseCaseProtocol {

    // MARK: - Properties

    private let fetchFoodsConsumedInRange: any FetchFoodsConsumedInRangeUseCaseProtocol
    private let fetchBodyWeights: any FetchBodyWeightsUseCaseProtocol
    private let fetchAppSettings: any FetchAppSettingsUseCaseProtocol
    private let reportFactory: FoodExportReportFactory
    private let directory: URL

    // MARK: - Init

    init(
        fetchFoodsConsumedInRange: any FetchFoodsConsumedInRangeUseCaseProtocol,
        fetchBodyWeights: any FetchBodyWeightsUseCaseProtocol,
        fetchAppSettings: any FetchAppSettingsUseCaseProtocol,
        reportFactory: FoodExportReportFactory = FoodExportReportFactory(),
        directory: URL = FileManager.default.temporaryDirectory
    ) {
        self.fetchFoodsConsumedInRange = fetchFoodsConsumedInRange
        self.fetchBodyWeights = fetchBodyWeights
        self.fetchAppSettings = fetchAppSettings
        self.reportFactory = reportFactory
        self.directory = directory
    }

    // MARK: - Functions

    func callAsFunction(from: Date, to: Date, format: FoodExportFormat, mealTypes: [MealTypeDomain]) async throws -> URL {
        async let foodsRead = fetchFoodsConsumedInRange(from: from, to: to)
        async let weightsRead = fetchBodyWeights(from: from, to: to)
        let (foods, weights) = try await (foodsRead, weightsRead)
        let unit = weights.isEmpty ? .kg : await readWeightUnit()
        let report = reportFactory.makeReport(
            foods: foods,
            mealTypes: mealTypes,
            from: from,
            to: to,
            weights: weights,
            weightUnit: unit
        )
        let data: Data
        switch format {
        case .pdf: data = NSDataBridgeKt.renderPdfData(report: report)
        case .xlsx: data = NSDataBridgeKt.renderXlsxData(report: report)
        }
        let url = directory.appendingPathComponent("Kalorie_\(isoDay(from))_\(isoDay(to)).\(format.fileExtension)")
        try data.write(to: url, options: .atomic)
        return url
    }

    // MARK: - Private

    private func readWeightUnit() async -> BodyWeightUnit {
        do {
            return try await fetchAppSettings().bodyWeightUnit
        } catch {
            Log.warning(error, category: Constants.LogCategory.export)
            return .kg
        }
    }

    private func isoDay(_ date: Date) -> String {
        date.formatted(Date.ISO8601FormatStyle(timeZone: .current).year().month().day())
    }
}

#if DEBUG
struct GenerateFoodExportUseCaseFake: GenerateFoodExportUseCaseProtocol {

    // MARK: - Properties

    var stubbedURL = URL(fileURLWithPath: "/dev/null")
    var stubbedError: Error?

    // MARK: - Functions

    func callAsFunction(from: Date, to: Date, format: FoodExportFormat, mealTypes: [MealTypeDomain]) async throws -> URL {
        if let stubbedError { throw stubbedError }
        return stubbedURL
    }
}
#endif
