//
//  FoodFrequencyDTO.swift
//  Kalorie
//
//  Created by Josef Antoni on 06.10.2026.
//

import Foundation
import MacroKit

struct FoodFrequencyItemDTO: Codable {

    // MARK: - Properties

    let id: String
    let foodItemKind: FoodItemKind
    let czName: String
    let engName: String
    let weight: Double
    let date: TimeInterval
    let energyKJ: Double?
    let caloriesPerHundredGrams: Double
    let fat: Double
    let fatSaturated: Double?
    let fatUnsaturatedFattyAcids: Double
    let carbohydrate: Double
    let carbohydratePureSugar: Double
    let fiber: Double?
    let protein: Double
    let salt: Double
    let portions: [FoodPortionDTO]?
    let measureUnit: String?
    let alcoholByVolume: Double?

    // MARK: - Coding keys

    enum CodingKeys: String, CodingKey {
        case id, weight, date, fat, carbohydrate, protein, salt, fiber, portions
        case czName = "cz_name"
        case engName = "eng_name"
        case energyKJ = "energy_kj"
        case caloriesPerHundredGrams = "calories_per_hundred_grams"
        case fatSaturated = "fat_saturated"
        case fatUnsaturatedFattyAcids = "fat_unsaturated_fatty_acids"
        case carbohydratePureSugar = "carbohydrate_pure_sugar"
        case foodItemKind = "food_item_kind"
        case measureUnit = "measure_unit"
        case alcoholByVolume = "alcohol_by_volume"
    }

    // MARK: - Init

    init(item: FoodItemDomain) {
        id = item.id
        foodItemKind = item.kind
        czName = item.czName
        engName = item.engName
        weight = item.weight
        date = item.date.timeIntervalSince1970
        energyKJ = item.energyKJ
        caloriesPerHundredGrams = item.caloriesPerHundredGrams
        fat = item.fat
        fatSaturated = item.fatSaturated
        fatUnsaturatedFattyAcids = item.fatUnsaturatedFattyAcids
        carbohydrate = item.carbohydrate
        carbohydratePureSugar = item.carbohydratePureSugar
        fiber = item.fiber
        protein = item.protein
        salt = item.salt
        portions = item.portions.map(FoodPortionDTO.init(portion:))
        measureUnit = item.measure.rawValue
        alcoholByVolume = item.alcoholByVolume
    }

    // MARK: - Functions

    func asDomain() -> FoodItemDomain {
        FoodItemDomain(
            id: id,
            kind: foodItemKind,
            czName: czName,
            engName: engName,
            weight: weight,
            date: Date(timeIntervalSince1970: date),
            energyKJ: energyKJ ?? MacrosKt.energyKJFromMacros(fat: fat, carbohydrate: carbohydrate, protein: protein),
            caloriesPerHundredGrams: caloriesPerHundredGrams,
            fat: fat,
            fatSaturated: fatSaturated,
            fatUnsaturatedFattyAcids: fatUnsaturatedFattyAcids,
            carbohydrate: carbohydrate,
            carbohydratePureSugar: carbohydratePureSugar,
            fiber: fiber,
            protein: protein,
            salt: salt,
            portions: portions?.map { $0.asDomain() } ?? [],
            measure: measureUnit.flatMap(FoodMeasure.init(rawValue:)) ?? .grams,
            alcoholByVolume: alcoholByVolume
        )
    }
}

struct FoodFrequencyEntryDTO: Decodable {

    // MARK: - Properties

    let count: Int
    let lastLoggedAt: TimeInterval
    let item: FoodFrequencyItemDTO

    // MARK: - Coding keys

    enum CodingKeys: String, CodingKey {
        case count, item
        case lastLoggedAt = "last_logged_at"
    }
}

struct FoodFrequencyDocumentDTO: Decodable {

    // MARK: - Properties

    let entries: [String: FoodFrequencyEntryDTO]
    let undecodableIds: [String]

    // MARK: - Coding keys

    enum CodingKeys: String, CodingKey {
        case entries
    }

    // MARK: - Init

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        let lossyEntries = try container.decodeIfPresent([String: LossyEntry].self, forKey: .entries) ?? [:]
        entries = lossyEntries.compactMapValues(\.entry)
        undecodableIds = lossyEntries.filter { $0.value.entry == nil }.map(\.key).sorted()
    }

    // MARK: - Types

    private struct LossyEntry: Decodable {
        let entry: FoodFrequencyEntryDTO?

        init(from decoder: Decoder) throws {
            entry = try? FoodFrequencyEntryDTO(from: decoder)
        }
    }
}
