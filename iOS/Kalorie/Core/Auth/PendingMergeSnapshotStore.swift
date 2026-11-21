//
//  PendingMergeSnapshotStore.swift
//  Kalorie
//
//  Created by Josef Antoni on 07.08.2026.
//

import Foundation
import TextKit

struct PendingMergeSnapshot: Codable {

    // MARK: - Properties

    let sourceAnonymousUserId: String
    let foodConsumed: [FoodConsumedDTO]
    let favouriteFoods: [FavouriteFoodDTO]
    let myCreatedMeals: [MyCreatedMealDTO]
    let foodItemPortions: [FoodItemPersonalPortionsDTO]
    let bodyWeights: [BodyWeightDTO]
    let appSettings: AppSettingsDTO?
    let dailyGoals: DailyGoalsDTO?

    // MARK: - Init

    init(
        sourceAnonymousUserId: String,
        foodConsumed: [FoodConsumedDTO],
        favouriteFoods: [FavouriteFoodDTO],
        myCreatedMeals: [MyCreatedMealDTO],
        foodItemPortions: [FoodItemPersonalPortionsDTO] = [],
        bodyWeights: [BodyWeightDTO] = [],
        appSettings: AppSettingsDTO? = nil,
        dailyGoals: DailyGoalsDTO? = nil
    ) {
        self.sourceAnonymousUserId = sourceAnonymousUserId
        self.foodConsumed = foodConsumed
        self.favouriteFoods = favouriteFoods
        self.myCreatedMeals = myCreatedMeals
        self.foodItemPortions = foodItemPortions
        self.bodyWeights = bodyWeights
        self.appSettings = appSettings
        self.dailyGoals = dailyGoals
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        sourceAnonymousUserId = try container.decode(String.self, forKey: .sourceAnonymousUserId)
        foodConsumed = try container.decode([FoodConsumedDTO].self, forKey: .foodConsumed)
        favouriteFoods = try container.decodeIfPresent([FavouriteFoodDTO].self, forKey: .favouriteFoods) ?? []
        myCreatedMeals = try container.decodeIfPresent([MyCreatedMealDTO].self, forKey: .myCreatedMeals) ?? []
        foodItemPortions = try container.decodeIfPresent([FoodItemPersonalPortionsDTO].self, forKey: .foodItemPortions) ?? []
        bodyWeights = try container.decodeIfPresent([BodyWeightDTO].self, forKey: .bodyWeights) ?? []
        appSettings = try container.decodeIfPresent(AppSettingsDTO.self, forKey: .appSettings)
        dailyGoals = try container.decodeIfPresent(DailyGoalsDTO.self, forKey: .dailyGoals)
    }
}

protocol PendingMergeSnapshotStoreProtocol {
    func save(_ snapshot: PendingMergeSnapshot) throws
    func load() throws -> PendingMergeSnapshot?
    func delete() throws
}

struct PendingMergeSnapshotStore: PendingMergeSnapshotStoreProtocol {

    // MARK: - Properties

    private var fileURL: URL {
        FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
            .appendingPathComponent(Constants.Auth.pendingMergeSnapshotFileName)
    }

    // MARK: - Functions

    func save(_ snapshot: PendingMergeSnapshot) throws {
        try JSONEncoder().encode(snapshot).write(to: fileURL, options: .atomic)
    }

    func load() throws -> PendingMergeSnapshot? {
        guard FileManager.default.fileExists(atPath: fileURL.path) else { return nil }
        let data = try Data(contentsOf: fileURL)
        return try JSONDecoder().decode(PendingMergeSnapshot.self, from: Self.fillingMissingSlovakNames(in: data))
    }

    func delete() throws {
        guard FileManager.default.fileExists(atPath: fileURL.path) else { return }
        try FileManager.default.removeItem(at: fileURL)
    }

    static func fillingMissingSlovakNames(in data: Data) -> Data {
        guard var root = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any] else { return data }
        var changed = false

        func filled(_ items: Any?) -> Any? {
            guard let items = items as? [[String: Any]] else { return items }
            return items.map { item -> [String: Any] in
                guard item["sk_name"] == nil else { return item }
                changed = true
                var item = item
                item["sk_name"] = NameRulesKt.slovakName(
                    slovak: nil,
                    czech: item["cz_name"] as? String,
                    english: item["eng_name"] as? String
                )
                return item
            }
        }

        if let foodConsumed = filled(root["foodConsumed"]) { root["foodConsumed"] = foodConsumed }
        if let favouriteFoods = filled(root["favouriteFoods"]) { root["favouriteFoods"] = favouriteFoods }
        if let meals = root["myCreatedMeals"] as? [[String: Any]] {
            root["myCreatedMeals"] = meals.map { meal -> [String: Any] in
                var meal = meal
                if let ingredients = filled(meal["ingredients"]) { meal["ingredients"] = ingredients }
                return meal
            }
        }

        guard changed, let result = try? JSONSerialization.data(withJSONObject: root) else { return data }
        return result
    }
}

#if DEBUG
final class PendingMergeSnapshotStoreFake: PendingMergeSnapshotStoreProtocol {

    // MARK: - Properties

    var stubbedSnapshot: PendingMergeSnapshot?
    var saveError: Error?
    var deleteError: Error?
    private(set) var deleteCallCount = 0

    // MARK: - Functions

    func save(_ snapshot: PendingMergeSnapshot) throws {
        if let saveError { throw saveError }
        stubbedSnapshot = snapshot
    }

    func load() throws -> PendingMergeSnapshot? {
        stubbedSnapshot
    }

    func delete() throws {
        deleteCallCount += 1
        if let deleteError { throw deleteError }
        stubbedSnapshot = nil
    }
}
#endif
