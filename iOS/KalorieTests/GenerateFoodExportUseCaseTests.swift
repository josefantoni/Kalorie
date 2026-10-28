//
//  GenerateFoodExportUseCaseTests.swift
//  KalorieTests
//
//  Created by Josef Antoni on 19.09.2026.
//

import PDFKit
import XCTest
@testable import Kalorie

final class GenerateFoodExportUseCaseTests: XCTestCase {

    // MARK: - Tests

    func test_generate_whenFetchFails_rethrowsItAndWritesNoFile() async throws {
        let (sut, directory) = try makeSUT(fetch: FetchFoodsConsumedInRangeUseCaseFake(stubbedError: AuthError.notAuthenticated))

        do {
            _ = try await sut(from: .now, to: .now, format: .pdf, mealTypes: [])
            XCTFail("Expected notAuthenticated error")
        } catch AuthError.notAuthenticated {}
        XCTAssertTrue(try FileManager.default.contentsOfDirectory(atPath: directory.path).isEmpty)
    }

    func test_generate_xlsx_writesAZipContainerNamedAfterTheInterval() async throws {
        let (sut, _) = try makeSUT()

        let url = try await sut(from: try makeDate(day: 1), to: try makeDate(day: 19), format: .xlsx, mealTypes: [])

        XCTAssertEqual(url.lastPathComponent, "Kalorie_2026-09-01_2026-09-19.xlsx")
        XCTAssertEqual(try Data(contentsOf: url).prefix(4), Data([0x50, 0x4B, 0x03, 0x04]))
    }

    func test_generate_pdf_writesAPdfDocument() async throws {
        let (sut, _) = try makeSUT()

        let url = try await sut(from: try makeDate(day: 1), to: try makeDate(day: 2), format: .pdf, mealTypes: [])

        XCTAssertEqual(url.pathExtension, "pdf")
        XCTAssertEqual(try Data(contentsOf: url).prefix(5), Data("%PDF-".utf8))
    }

    func test_generate_pdf_aLongIntervalSpillsOntoMorePages() async throws {
        let (sut, _) = try makeSUT()

        let url = try await sut(from: try makeDate(day: 1), to: try makeDate(day: 30), format: .pdf, mealTypes: [])

        let document = try XCTUnwrap(PDFDocument(url: url))
        XCTAssertGreaterThan(document.pageCount, 1)
    }

    func test_generate_withNoEntriesInTheInterval_stillProducesAFile() async throws {
        let (sut, _) = try makeSUT(fetch: FetchFoodsConsumedInRangeUseCaseFake(stubbedFoods: []))

        let url = try await sut(from: try makeDate(day: 1), to: try makeDate(day: 3), format: .xlsx, mealTypes: [])

        XCTAssertTrue(FileManager.default.fileExists(atPath: url.path))
    }

    func test_export_whenWeightsCannotBeRead_failsLoudInsteadOfSilentlyDroppingThem() async throws {
        let (sut, _) = try makeSUT(weights: FetchBodyWeightsUseCaseFake(shouldThrow: true))

        do {
            _ = try await sut(from: Date(), to: Date(), format: .xlsx, mealTypes: [])
            XCTFail("Expected the weight read to propagate")
        } catch {
            XCTAssertTrue(error is URLError)
        }
    }

    func test_export_whenSettingsCannotBeRead_stillProducesAFileInKilograms() async throws {
        let weights = FetchBodyWeightsUseCaseFake(stubbedWeights: [BodyWeightDomain(dayKey: "2026-09-01", kilograms: 78.4)])
        let (sut, _) = try makeSUT(weights: weights, settings: FetchAppSettingsUseCaseFake(shouldThrow: true))

        let url = try await sut(from: try makeDate(day: 1), to: try makeDate(day: 3), format: .xlsx, mealTypes: [])

        XCTAssertTrue(FileManager.default.fileExists(atPath: url.path))
    }

    func test_export_withoutAnyWeight_doesNotReadTheSettings() async throws {
        let settings = CountingFetchAppSettings()
        let (sut, _) = try makeSUT(settings: settings)

        _ = try await sut(from: try makeDate(day: 1), to: try makeDate(day: 3), format: .xlsx, mealTypes: [])

        XCTAssertEqual(settings.callCount, 0, "the unit is only needed to format a weight, so a user without weights must not pay for the read")
    }

    func test_export_withAWeight_readsTheSettingsOnce() async throws {
        let weights = FetchBodyWeightsUseCaseFake(stubbedWeights: [BodyWeightDomain(dayKey: "2026-09-01", kilograms: 78.4)])
        let settings = CountingFetchAppSettings()
        let (sut, _) = try makeSUT(weights: weights, settings: settings)

        _ = try await sut(from: try makeDate(day: 1), to: try makeDate(day: 3), format: .xlsx, mealTypes: [])

        XCTAssertEqual(settings.callCount, 1)
    }

    // MARK: - Helpers

    private func makeSUT(
        fetch: FetchFoodsConsumedInRangeUseCaseFake = FetchFoodsConsumedInRangeUseCaseFake(),
        weights: FetchBodyWeightsUseCaseFake = FetchBodyWeightsUseCaseFake(),
        settings: any FetchAppSettingsUseCaseProtocol = FetchAppSettingsUseCaseFake()
    ) throws -> (sut: GenerateFoodExportUseCase, directory: URL) {
        let directory = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        addTeardownBlock { try? FileManager.default.removeItem(at: directory) }
        return (GenerateFoodExportUseCase(
            fetchFoodsConsumedInRange: fetch,
            fetchBodyWeights: weights,
            fetchAppSettings: settings,
            directory: directory
        ), directory)
    }

    private func makeDate(day: Int) throws -> Date {
        try XCTUnwrap(Calendar.current.date(from: DateComponents(year: 2026, month: 9, day: day, hour: 12)))
    }
}

private final class CountingFetchAppSettings: FetchAppSettingsUseCaseProtocol {

    // MARK: - Properties

    private(set) var callCount = 0

    // MARK: - Functions

    func callAsFunction() async throws -> AppSettingsDomain {
        callCount += 1
        return .defaults
    }
}
