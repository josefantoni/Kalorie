//
//  DailyGoalsUseCasesTests.swift
//  KalorieTests
//
//  Created by Josef Antoni on 06.11.2026.
//

import XCTest
@testable import Kalorie

final class DailyGoalsUseCasesTests: XCTestCase {

    // MARK: - History

    func test_goalOnDay_usesLatestEntryNotAfterTheDay() {
        let history = DailyGoalHistory(entries: [
            DailyGoalDomain(validFrom: "2026-01-10", kcalGoal: 2000),
            DailyGoalDomain(validFrom: "2026-06-01", kcalGoal: 2400)
        ])
        XCTAssertNil(history.goal(onDayKey: "2026-01-09"))
        XCTAssertEqual(history.goal(onDayKey: "2026-03-01")?.kcalGoal, 2000)
        XCTAssertEqual(history.goal(onDayKey: "2026-06-01")?.kcalGoal, 2400)
    }

    func test_goalOnDay_afterEndedEntry_isNilButEarlierDaysKeepTheirGoal() {
        let history = DailyGoalHistory(entries: [
            DailyGoalDomain(validFrom: "2026-01-10", kcalGoal: 2000),
            .ended(validFrom: "2026-05-01")
        ])
        XCTAssertEqual(history.goal(onDayKey: "2026-04-30")?.kcalGoal, 2000)
        XCTAssertNil(history.goal(onDayKey: "2026-05-01"))
    }

    func test_replacing_sameDayReplacesEntry() {
        let history = DailyGoalHistory(entries: [DailyGoalDomain(validFrom: "2026-01-10", kcalGoal: 2000)])
            .replacing(DailyGoalDomain(validFrom: "2026-01-10", kcalGoal: 2200))
        XCTAssertEqual(history.entries.count, 1)
        XCTAssertEqual(history.entries.first?.kcalGoal, 2200)
    }

    func test_replacing_dropsEntriesDatedAfterTheNewOne() {
        let history = DailyGoalHistory(entries: [DailyGoalDomain(validFrom: "2026-01-12", kcalGoal: 1800)])
            .replacing(DailyGoalDomain(validFrom: "2026-01-11", kcalGoal: 2200))
        XCTAssertEqual(history.entries.count, 1)
        XCTAssertEqual(history.goal(onDayKey: "2026-01-12")?.kcalGoal, 2200)
    }

    func test_replacing_endedGoalDropsLaterEntries() {
        let history = DailyGoalHistory(entries: [DailyGoalDomain(validFrom: "2026-01-12", kcalGoal: 1800)])
            .replacing(.ended(validFrom: "2026-01-11"))
        XCTAssertNil(history.goal(onDayKey: "2026-01-12"))
    }

    func test_replacing_keepsEarlierEntries() {
        let history = DailyGoalHistory(entries: [DailyGoalDomain(validFrom: "2026-01-05", kcalGoal: 2000)])
            .replacing(DailyGoalDomain(validFrom: "2026-01-11", kcalGoal: 2200))
        XCTAssertEqual(history.entries.map(\.validFrom), ["2026-01-05", "2026-01-11"])
    }

    // MARK: - Estimate

    func test_estimate_matchesOwnerSanityCheck() throws {
        let estimate = try XCTUnwrap(GoalEstimate(sex: .male, birthYear: 1991, heightCm: 188, weightKg: 92, activityLevel: .sedentary, currentYear: 2026))
        XCTAssertEqual(estimate.basalRate, 1925, accuracy: 1)
        XCTAssertEqual(estimate.expenditure, 2310, accuracy: 2)
        XCTAssertEqual(estimate.variants.map(\.percentChange), [-10, -5, 0, 5, 10])
        XCTAssertEqual(estimate.variants[2].kcal, estimate.expenditure)
    }

    func test_estimate_missingInput_isNil() {
        XCTAssertNil(GoalEstimate(sex: nil, birthYear: 1991, heightCm: 188, weightKg: 92, activityLevel: .light, currentYear: 2026))
    }

    // MARK: - Wire format

    func test_dto_roundTripsPercentGoalWithCalculatorInputs() throws {
        var goal = DailyGoalDomain(validFrom: "2026-11-06", kcalGoal: 2100, macroMode: .percent)
        goal.proteinPercent = 20
        goal.carbsPercent = 50
        goal.fatPercent = 30
        goal.sex = .female
        goal.birthYear = 1990
        goal.activityLevel = .veryHigh
        let history = DailyGoalHistory(entries: [goal])
        XCTAssertEqual(DailyGoalsDTO(history: history).asDomain(), history)
    }

    func test_dto_wrongTypedFieldReadsAsAbsentAndKeepsTheEntry() throws {
        let json = """
        {"goals":[
            {"valid_from":"2026-01-01","kcal_goal":2000,"birth_year":1990.5,"weight_kg":80},
            {"valid_from":"2026-02-01","kcal_goal":1800}
        ]}
        """
        let history = try JSONDecoder().decode(DailyGoalsDTO.self, from: Data(json.utf8)).asDomain()
        XCTAssertEqual(history.entries.map(\.validFrom), ["2026-01-01", "2026-02-01"])
        XCTAssertEqual(history.entries[0].kcalGoal, 2000)
        XCTAssertNil(history.entries[0].birthYear)
        XCTAssertEqual(history.entries[0].weightKg, 80)
        XCTAssertEqual(history.entries[1].kcalGoal, 1800)
    }

    func test_dto_unreadableEntryIsSkippedAndTheRestSurvives() throws {
        let json = #"{"goals":["oops",{"valid_from":"2026-02-01","kcal_goal":1800}]}"#
        let history = try JSONDecoder().decode(DailyGoalsDTO.self, from: Data(json.utf8)).asDomain()
        XCTAssertEqual(history.entries.map(\.validFrom), ["2026-02-01"])
    }

    func test_dto_nonArrayGoalsReadsAsEmptyHistory() throws {
        let json = #"{"goals":"oops"}"#
        let history = try JSONDecoder().decode(DailyGoalsDTO.self, from: Data(json.utf8)).asDomain()
        XCTAssertTrue(history.entries.isEmpty)
    }

    func test_dto_unknownEnumValuesReadAsAbsent() throws {
        let entry = DailyGoalEntryDTO(goal: DailyGoalDomain(validFrom: "2026-11-06", kcalGoal: 2000))
        let json = try JSONEncoder().encode(entry)
        var object = try XCTUnwrap(JSONSerialization.jsonObject(with: json) as? [String: Any])
        object["sex"] = "other"
        object["activity_level"] = "extreme"
        let decoded = try JSONDecoder().decode(DailyGoalEntryDTO.self, from: JSONSerialization.data(withJSONObject: object))
        XCTAssertNil(decoded.asDomain()?.sex)
        XCTAssertNil(decoded.asDomain()?.activityLevel)
    }

    func test_dto_unknownMacroModeSkipsTheEntryInsteadOfReadingAsPercent() throws {
        let entry = DailyGoalEntryDTO(goal: DailyGoalDomain(validFrom: "2026-11-06", kcalGoal: 2000))
        let json = try JSONEncoder().encode(entry)
        var object = try XCTUnwrap(JSONSerialization.jsonObject(with: json) as? [String: Any])
        object["macro_mode"] = "mixed"
        let decoded = try JSONDecoder().decode(DailyGoalEntryDTO.self, from: JSONSerialization.data(withJSONObject: object))
        XCTAssertNil(decoded.asDomain())
    }

    func test_dto_wrongTypedMacroModeSkipsTheEntryInsteadOfReadingAsPercent() throws {
        let entry = DailyGoalEntryDTO(goal: DailyGoalDomain(validFrom: "2026-11-06", kcalGoal: 2000))
        let json = try JSONEncoder().encode(entry)
        var object = try XCTUnwrap(JSONSerialization.jsonObject(with: json) as? [String: Any])
        object["macro_mode"] = 5
        let decoded = try JSONDecoder().decode(DailyGoalEntryDTO.self, from: JSONSerialization.data(withJSONObject: object))
        XCTAssertNil(decoded.asDomain())
    }

    func test_dto_wrongTypedKcalGoalSkipsTheEntryAndThePreviousGoalStays() throws {
        let json = """
        {"goals":[
            {"valid_from":"2026-01-01","kcal_goal":2000},
            {"valid_from":"2026-02-01","kcal_goal":"1800"}
        ]}
        """
        let history = try JSONDecoder().decode(DailyGoalsDTO.self, from: Data(json.utf8)).asDomain()
        XCTAssertEqual(history.entries.map(\.validFrom), ["2026-01-01"])
        XCTAssertEqual(history.goal(onDayKey: "2026-03-01")?.kcalGoal, 2000)
    }

    func test_dto_explicitNullKcalGoalStillReadsAsEndedGoal() throws {
        let json = #"{"goals":[{"valid_from":"2026-02-01","kcal_goal":null}]}"#
        let history = try JSONDecoder().decode(DailyGoalsDTO.self, from: Data(json.utf8)).asDomain()
        XCTAssertEqual(history.entries.map(\.validFrom), ["2026-02-01"])
        XCTAssertNil(history.entries[0].kcalGoal)
    }

    func test_dto_endedGoalWithoutMacroModeStillReads() {
        let ended = DailyGoalEntryDTO(goal: .ended(validFrom: "2026-11-06"))
        XCTAssertNotNil(ended.asDomain())
    }

    // MARK: - Save

    func test_save_stampsTodayAndMergesIntoGoalsDocument() async throws {
        let (sut, dataProvider) = makeSaveSUT()
        _ = try await sut(percentGoal(kcal: 2000))
        XCTAssertEqual(dataProvider.mergedId, "goals")
        XCTAssertEqual(dataProvider.mergedCollection, "users/user-1/settings")
        XCTAssertEqual(dataProvider.mergedGoals?.asDomain().entries.first?.validFrom, "2026-11-06")
    }

    func test_save_secondSaveSameDay_replacesEntry() async throws {
        let (sut, dataProvider) = makeSaveSUT()
        dataProvider.storedGoals = DailyGoalsDTO(history: DailyGoalHistory(entries: [DailyGoalDomain(validFrom: "2026-11-06", kcalGoal: 1800)]))
        let history = try await sut(percentGoal(kcal: 2000))
        XCTAssertEqual(history.entries.count, 1)
        XCTAssertEqual(history.entries.first?.kcalGoal, 2000)
    }

    func test_save_keepsEarlierEntries() async throws {
        let (sut, dataProvider) = makeSaveSUT()
        dataProvider.storedGoals = DailyGoalsDTO(history: DailyGoalHistory(entries: [DailyGoalDomain(validFrom: "2026-01-01", kcalGoal: 1800)]))
        let history = try await sut(percentGoal(kcal: 2000))
        XCTAssertEqual(history.entries.map(\.validFrom), ["2026-01-01", "2026-11-06"])
    }

    func test_save_percentNotSummingTo100_throwsAndWritesNothing() async {
        let (sut, dataProvider) = makeSaveSUT()
        var goal = percentGoal(kcal: 2000)
        goal.fatPercent = 40
        await assertThrows(sut, goal, .invalidMacros)
        XCTAssertNil(dataProvider.mergedId)
    }

    func test_save_percentModeWithNegativeMacro_throwsAndWritesNothing() async {
        let (sut, dataProvider) = makeSaveSUT()
        var goal = percentGoal(kcal: 2000)
        goal.proteinPercent = 120
        goal.carbsPercent = -20
        goal.fatPercent = 0
        await assertThrows(sut, goal, .invalidMacros)
        XCTAssertNil(dataProvider.mergedId)
    }

    func test_save_gramsModeWithNegativeMacro_throws() async {
        let (sut, _) = makeSaveSUT()
        var goal = DailyGoalDomain(validFrom: "", kcalGoal: 2000, macroMode: .grams)
        goal.proteinGrams = 150
        goal.carbsGrams = -10
        goal.fatGrams = 60
        await assertThrows(sut, goal, .invalidMacros)
    }

    func test_save_gramsModeAboveLimit_throwsAndWritesNothing() async {
        let (sut, dataProvider) = makeSaveSUT()
        var goal = DailyGoalDomain(validFrom: "", kcalGoal: 2000, macroMode: .grams)
        goal.proteinGrams = 99_999_999
        goal.carbsGrams = 200
        goal.fatGrams = 60
        await assertThrows(sut, goal, .invalidMacros)
        XCTAssertNil(dataProvider.mergedId)
    }

    func test_save_gramsModeAtLimit_saves() async throws {
        let (sut, _) = makeSaveSUT()
        var goal = DailyGoalDomain(validFrom: "", kcalGoal: 2000, macroMode: .grams)
        goal.proteinGrams = 1000
        goal.carbsGrams = 200
        goal.fatGrams = 60
        _ = try await sut(goal)
    }

    func test_profileEntry_skipsEntriesWithoutProfile() {
        var withProfile = DailyGoalDomain(validFrom: "2026-01-01", kcalGoal: 2000)
        withProfile.birthYear = 1990
        let history = DailyGoalHistory(entries: [withProfile, .ended(validFrom: "2026-02-01")])
        XCTAssertEqual(history.latestProfileEntry(onDayKey: "2026-03-01")?.validFrom, "2026-01-01")
        XCTAssertNil(history.latestProfileEntry(onDayKey: "2025-12-31"))
    }

    func test_save_kcalOutOfRange_throws() async {
        let (sut, _) = makeSaveSUT()
        await assertThrows(sut, percentGoal(kcal: 100), .invalidKcal)
    }

    func test_save_endedGoal_writesEntryWithoutKcal() async throws {
        let (sut, dataProvider) = makeSaveSUT()
        dataProvider.storedGoals = DailyGoalsDTO(history: DailyGoalHistory(entries: [DailyGoalDomain(validFrom: "2026-01-01", kcalGoal: 1800)]))
        let history = try await sut(.ended(validFrom: ""))
        XCTAssertNil(history.goal(onDayKey: "2026-11-06"))
        XCTAssertEqual(history.goal(onDayKey: "2026-11-05")?.kcalGoal, 1800)
    }

    func test_save_endedGoal_onTheDayOfASavedGoal_keepsTheProfile() async throws {
        let (sut, dataProvider) = makeSaveSUT()
        var saved = DailyGoalDomain(validFrom: "2026-11-06", kcalGoal: 1800)
        saved.sex = .female
        saved.birthYear = 1990
        saved.heightCm = 170
        saved.weightKg = 65
        saved.activityLevel = .moderate
        dataProvider.storedGoals = DailyGoalsDTO(history: DailyGoalHistory(entries: [saved]))
        let history = try await sut(.ended(validFrom: ""))
        XCTAssertNil(history.goal(onDayKey: "2026-11-06"))
        let profile = try XCTUnwrap(history.latestProfileEntry(onDayKey: "2026-11-06"))
        XCTAssertEqual(profile.birthYear, 1990)
        XCTAssertEqual(profile.heightCm, 170)
        XCTAssertEqual(profile.activityLevel, .moderate)
        XCTAssertNil(profile.kcalGoal)
    }

    func test_save_unauthenticated_throws() async {
        let (sut, _) = makeSaveSUT(userId: nil)
        do {
            _ = try await sut(percentGoal(kcal: 2000))
            XCTFail("Expected notAuthenticated")
        } catch {
            XCTAssertEqual(error as? AuthError, .notAuthenticated)
        }
    }

    // MARK: - Helpers

    private func percentGoal(kcal: Double) -> DailyGoalDomain {
        var goal = DailyGoalDomain(validFrom: "", kcalGoal: kcal, macroMode: .percent)
        goal.proteinPercent = 20
        goal.carbsPercent = 50
        goal.fatPercent = 30
        return goal
    }

    private func assertThrows(_ sut: SaveDailyGoalUseCase, _ goal: DailyGoalDomain, _ expected: DailyGoalError) async {
        do {
            _ = try await sut(goal)
            XCTFail("Expected \(expected)")
        } catch {
            XCTAssertEqual(error as? DailyGoalError, expected)
        }
    }

    private func makeSaveSUT(userId: String? = "user-1") -> (sut: SaveDailyGoalUseCase, dataProvider: DailyGoalsDataProviderFake) {
        let dataProvider = DailyGoalsDataProviderFake()
        let authProvider = AuthProviderFake(userId: userId)
        let now = { Date(timeIntervalSince1970: 1_793_950_400) }
        let sut = SaveDailyGoalUseCase(
            fetchDailyGoals: FetchDailyGoalsUseCase(dataProvider: dataProvider, authProvider: authProvider),
            dataProvider: dataProvider,
            authProvider: authProvider,
            now: now
        )
        return (sut, dataProvider)
    }
}

private final class DailyGoalsDataProviderFake: FirestoreDataProviderProtocol {

    // MARK: - Properties

    var setId: String?
    var setCollection: String?
    var setWeightKg: Double?
    var mergedId: String?
    var mergedCollection: String?
    var queriedUpperBound: String?
    var queriedLimit: Int?
    var queriedLowerBound: String?
    var queriedAscendingLimit: Int?
    var queriedRange: (lower: String, upper: String)?

    // MARK: - Functions

    func loadAsync<T: Decodable>(from collection: String) async throws -> [T] { [] }
    func loadFromServerAsync<T: Decodable>(from collection: String) async throws -> [T] { [] }
    func loadAsync<T: Decodable>(from collection: String, where field: String, isGreaterThanOrEqualTo lowerBound: Double, isLessThan upperBound: Double) async throws -> [T] { [] }
    func loadAsync<T: Decodable>(from collection: String, where field: String, hasPrefix prefix: String, limit: Int) async throws -> [T] { [] }
    func loadAsync<T: Decodable>(from collection: String, where field: String, arrayContains value: String, limit: Int) async throws -> [T] { [] }
    func loadAsync<T: Decodable>(from collection: String, where field: String, isEqualTo value: String) async throws -> T? { nil }
    func loadAsync<T: Decodable>(from collection: String, where field: String, isEqualTo value: String, orderBy orderField: String, descending: Bool) async throws -> [T] { [] }
    var storedGoals: DailyGoalsDTO?
    var mergedGoals: DailyGoalsDTO?

    func loadAsync<T: Decodable>(id: String, from collection: String) async throws -> T? { storedGoals as? T }
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

    func loadAsync<T: Decodable>(from collection: String, where field: String, isGreaterThan value: String, orderByAscending limit: Int) async throws -> [T] {
        queriedLowerBound = value
        queriedAscendingLimit = limit
        return []
    }

    func loadAsync<T: Decodable>(from collection: String, where field: String, isGreaterThanOrEqualTo lowerBound: String, isLessThanOrEqualTo upperBound: String) async throws -> [T] {
        queriedRange = (lowerBound, upperBound)
        return []
    }

    func mergeAsync<T: Encodable>(_ item: T, id: String, in collection: String) async throws {
        mergedId = id
        mergedCollection = collection
        mergedGoals = item as? DailyGoalsDTO
    }
}

