//
//  FoodItemDTO.swift
//  Kalorie
//
//  Created by Josef Antoni on 26.06.2024.
//

import Foundation
import MacroKit

public struct FoodItemDTO: Codable {

    // MARK: - Properties

    let id: String
    let czName: String
    let engName: String
    let czNameLowercase: String
    let engNameLowercase: String
    let czNameFolded: String?
    let engNameFolded: String?
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

    // MARK: - Coding keys

    enum CodingKeys: String, CodingKey {
        case id, weight, date, fat, carbohydrate, protein, salt, fiber
        case czName = "cz_name"
        case engName = "eng_name"
        case czNameLowercase = "cz_name_lowercase"
        case engNameLowercase = "eng_name_lowercase"
        case czNameFolded = "cz_name_folded"
        case engNameFolded = "eng_name_folded"
        case energyKJ = "energy_kj"
        case caloriesPerHundredGrams = "calories_per_hundred_grams"
        case fatSaturated = "fat_saturated"
        case fatUnsaturatedFattyAcids = "fat_unsaturated_fatty_acids"
        case carbohydratePureSugar = "carbohydrate_pure_sugar"
    }

    // MARK: - Init

    init(item: FoodItemDomain) {
        id = item.id
        czName = item.czName
        engName = item.engName
        czNameLowercase = item.czName.lowercased()
        engNameLowercase = item.engName.lowercased()
        czNameFolded = item.czName.lowercased().foldingDiacritics()
        engNameFolded = item.engName.lowercased().foldingDiacritics()
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
    }

    // MARK: - Functions

    func asDomain() -> FoodItemDomain {
        FoodItemDomain(
            id: id,
            kind: .catalogue,
            czName: czName,
            engName: engName,
            weight: weight,
            date: date.toDate,
            energyKJ: energyKJ ?? MacrosKt.energyKJFromMacros(fat: fat, carbohydrate: carbohydrate, protein: protein),
            caloriesPerHundredGrams: caloriesPerHundredGrams,
            fat: fat,
            fatSaturated: fatSaturated,
            fatUnsaturatedFattyAcids: fatUnsaturatedFattyAcids,
            carbohydrate: carbohydrate,
            carbohydratePureSugar: carbohydratePureSugar,
            fiber: fiber,
            protein: protein,
            salt: salt
        )
    }
}
