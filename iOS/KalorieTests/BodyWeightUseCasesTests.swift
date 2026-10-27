//
//  BodyWeightUseCasesTests.swift
//  KalorieTests
//
//  Created by Josef Antoni on 27.10.2026.
//

import XCTest
@testable import Kalorie

final class BodyWeightUseCasesTests: XCTestCase {

    // MARK: - Day key

    func test_dayKey_formatsGregorianDateInTimeZone() {
        let date = Date(timeIntervalSince1970: 1_792_000_000)
        let key = BodyWeightDayKey.make(from: date, timeZone: TimeZone(identifier: "Europe/Prague") ?? .gmt)
        XCTAssertEqual(key, "2026-10-14")
    }

    func test_dayKey_followsTimeZone() {
        let date = Date(timeIntervalSince1970: 1_792_015_800)
        XCTAssertNotEqual(
            BodyWeightDayKey.make(from: date, timeZone: TimeZone(identifier: "Pacific/Auckland") ?? .gmt),
            BodyWeightDayKey.make(from: date, timeZone: TimeZone(identifier: "America/Los_Angeles") ?? .gmt)
        )
    }

    func test_dayKey_matchesSharedFixtureCases() throws {
        let fixture: DayKeyFixture = try FixtureLoader.load("body-weight-day-key-cases")
        for dayKeyCase in fixture.cases {
            let instant = try XCTUnwrap(ISO8601DateFormatter().date(from: dayKeyCase.instant), dayKeyCase.name)
            let timeZone = try XCTUnwrap(TimeZone(identifier: dayKeyCase.timeZone), dayKeyCase.name)
            XCTAssertEqual(BodyWeightDayKey.make(from: instant, timeZone: timeZone), dayKeyCase.expectedKey, dayKeyCase.name)
        }
    }

    // MARK: - Save

    func test_save_writesToDayKeyDocumentInUserCollection() async throws {
        let (sut, dataProvider) = makeSaveSUT()
        let day = Date(timeIntervalSince1970: 1_792_000_000)
        try await sut(kilograms: 78.44, for: day)
        XCTAssertEqual(dataProvider.setId, BodyWeightDayKey.make(from: day))
        XCTAssertEqual(dataProvider.setCollection, "users/user-1/bodyWeights")
        XCTAssertEqual(dataProvider.setWeightKg, 78.44)
    }

    func test_save_futureDay_throwsAndWritesNothing() async {
        let (sut, dataProvider) = makeSaveSUT()
        do {
            try await sut(kilograms: 70, for: Date().addingTimeInterval(3 * 86_400))
            XCTFail("Expected futureDay")
        } catch {
            XCTAssertEqual(error as? BodyWeightError, .futureDay)
        }
        XCTAssertNil(dataProvider.setId)
    }

    func test_save_outOfRange_throwsAndWritesNothing() async {
        let (sut, dataProvider) = makeSaveSUT()
        do {
            try await sut(kilograms: 29.9, for: Date())
            XCTFail("Expected outOfRange")
        } catch {
            XCTAssertEqual(error as? BodyWeightError, .outOfRange)
        }
        XCTAssertNil(dataProvider.setId)
    }

    func test_save_notAuthenticated_throws() async {
        let (sut, _) = makeSaveSUT(userId: nil)
        do {
            try await sut(kilograms: 70, for: Date())
            XCTFail("Expected notAuthenticated")
        } catch AuthError.notAuthenticated {
        } catch {
            XCTFail("Unexpected \(error)")
        }
    }

    // MARK: - Export range

    func test_fetchForRange_oneQueryUpToLastDay_limitCoversTheDaysPlusTheLastEarlierEntry() async throws {
        let dataProvider = BodyWeightDataProviderFake()
        let sut = FetchBodyWeightsUseCase(dataProvider: dataProvider, authProvider: AuthProviderFake(userId: "user-1"))
        let from = try XCTUnwrap(BodyWeightDayKey.date(from: "2026-09-01"))
        let to = try XCTUnwrap(BodyWeightDayKey.date(from: "2026-09-10"))

        _ = try await sut(from: from, to: to)

        XCTAssertEqual(dataProvider.queriedUpperBound, "2026-09-10")
        XCTAssertEqual(dataProvider.queriedLimit, 11, "10 days plus one entry before the interval, so the first days still know their weight")
    }

    // MARK: - Settings

    func test_settings_absentDocument_readsAsDefaults() async throws {
        let sut = FetchAppSettingsUseCase(dataProvider: BodyWeightDataProviderFake(), authProvider: AuthProviderFake(userId: "user-1"))
        let settings = try await sut()
        XCTAssertEqual(settings, .defaults)
    }

    func test_settings_unknownUnit_readsAsKilograms() {
        let dto = AppSettingsDTO(settings: AppSettingsDomain(showBodyWeight: false, bodyWeightUnit: .lb))
        XCTAssertEqual(dto.asDomain().bodyWeightUnit, .lb)
        XCTAssertNil(BodyWeightUnit(rawValue: "stone"))
    }

    func test_settings_save_usesMergeNotSet() async throws {
        let dataProvider = BodyWeightDataProviderFake()
        let sut = SaveAppSettingsUseCase(dataProvider: dataProvider, authProvider: AuthProviderFake(userId: "user-1"))
        try await sut(AppSettingsDomain(showBodyWeight: false, bodyWeightUnit: .lb))
        XCTAssertEqual(dataProvider.mergedId, "app")
        XCTAssertEqual(dataProvider.mergedCollection, "users/user-1/settings")
        XCTAssertNil(dataProvider.setId)
    }

    // MARK: - Units

    func test_unit_poundsRoundTripExactlyThroughKilograms() {
        let kilograms = BodyWeightUnit.lb.kilograms(from: 180)
        XCTAssertEqual(BodyWeightUnit.lb.display(kilograms: kilograms), 180, accuracy: 1e-9)
    }

    // MARK: - Helpers

    private func makeSaveSUT(userId: String? = "user-1") -> (sut: SaveBodyWeightUseCase, dataProvider: BodyWeightDataProviderFake) {
        let dataProvider = BodyWeightDataProviderFake()
        let sut = SaveBodyWeightUseCase(dataProvider: dataProvider, authProvider: AuthProviderFake(userId: userId))
        return (sut, dataProvider)
    }
}

private final class BodyWeightDataProviderFake: FirestoreDataProviderProtocol {

    // MARK: - Properties

    var setId: String?
    var setCollection: String?
    var setWeightKg: Double?
    var mergedId: String?
    var mergedCollection: String?
    var queriedUpperBound: String?
    var queriedLimit: Int?

    // MARK: - Functions

    func loadAsync<T: Decodable>(from collection: String) async throws -> [T] { [] }
    func loadFromServerAsync<T: Decodable>(from collection: String) async throws -> [T] { [] }
    func loadAsync<T: Decodable>(from collection: String, where field: String, isGreaterThanOrEqualTo lowerBound: Double, isLessThan upperBound: Double) async throws -> [T] { [] }
    func loadAsync<T: Decodable>(from collection: String, where field: String, hasPrefix prefix: String, limit: Int) async throws -> [T] { [] }
    func loadAsync<T: Decodable>(from collection: String, where field: String, arrayContains value: String, limit: Int) async throws -> [T] { [] }
    func loadAsync<T: Decodable>(from collection: String, where field: String, isEqualTo value: String) async throws -> T? { nil }
    func loadAsync<T: Decodable>(from collection: String, where field: String, isEqualTo value: String, orderBy orderField: String, descending: Bool) async throws -> [T] { [] }
    func loadAsync<T: Decodable>(id: String, from collection: String) async throws -> T? { nil }
    func loadFromServerAsync<T: Decodable>(id: String, from collection: String) async throws -> T? { nil }
    func loadAsync<T: Decodable>(from collection: String, orderBy field: String, descending: Bool, limit: Int) async throws -> [T] { [] }
    func saveAsync<T: Encodable>(_ item: T, to collection: String) async throws {}
    func batchSetAsync<T: Encodable>(_ items: [(item: T, id: String)], in collection: String) async throws {}
    func deleteAsync(id: String, from collection: String) async throws {}

    func setAsync<T: Encodable>(_ item: T, id: String, in collection: String) async throws {
        setId = id
        setCollection = collection
        setWeightKg = (item as? BodyWeightDTO)?.weightKg
    }

    func loadAsync<T: Decodable>(from collection: String, where field: String, isLessThanOrEqualTo value: String, orderByDescending limit: Int) async throws -> [T] {
        queriedUpperBound = value
        queriedLimit = limit
        return []
    }

    func mergeAsync<T: Encodable>(_ item: T, id: String, in collection: String) async throws {
        mergedId = id
        mergedCollection = collection
    }
}

private struct DayKeyFixture: Decodable {
    let cases: [DayKeyCase]
}

private struct DayKeyCase: Decodable {
    let name: String
    let instant: String
    let timeZone: String
    let expectedKey: String
}
