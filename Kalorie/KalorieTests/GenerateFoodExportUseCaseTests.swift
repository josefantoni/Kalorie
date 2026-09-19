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

    // MARK: - Helpers

    private func makeSUT(
        fetch: FetchFoodsConsumedInRangeUseCaseFake = FetchFoodsConsumedInRangeUseCaseFake()
    ) throws -> (sut: GenerateFoodExportUseCase, directory: URL) {
        let directory = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        addTeardownBlock { try? FileManager.default.removeItem(at: directory) }
        return (GenerateFoodExportUseCase(fetchFoodsConsumedInRange: fetch, directory: directory), directory)
    }

    private func makeDate(day: Int) throws -> Date {
        try XCTUnwrap(Calendar.current.date(from: DateComponents(year: 2026, month: 9, day: day, hour: 12)))
    }
}
