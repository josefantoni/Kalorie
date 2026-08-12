//
//  FavouriteFoodDTO.swift
//  Kalorie
//
//  Created by Josef Antoni on 12.08.2026.
//

import Foundation

struct FavouriteFoodDTO: Codable {

    // MARK: - Properties

    let id: String
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
    let favouritedAt: TimeInterval

    // MARK: - Coding keys

    enum CodingKeys: String, CodingKey {
        case id, weight, date, fat, carbohydrate, protein, salt, fiber
        case czName = "cz_name"
        case engName = "eng_name"
        case energyKJ = "energy_kj"
        case caloriesPerHundredGrams = "calories_per_hundred_grams"
        case fatSaturated = "fat_saturated"
        case fatUnsaturatedFattyAcids = "fat_unsaturated_fatty_acids"
        case carbohydratePureSugar = "carbohydrate_pure_sugar"
        case favouritedAt = "favourited_at"
    }

    // MARK: - Init

    init(item: FoodItemDomain, favouritedAt: Date) {
        id = item.id
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
        self.favouritedAt = favouritedAt.timeIntervalSince1970
    }

    // MARK: - Functions

    func asDomain() -> FoodItemDomain {
        FoodItemDomain(
            id: id,
            czName: czName,
            engName: engName,
            weight: weight,
            date: Date(timeIntervalSince1970: date),
            energyKJ: energyKJ ?? 0,
            caloriesPerHundredGrams: caloriesPerHundredGrams,
            fat: fat,
            fatSaturated: fatSaturated ?? 0,
            fatUnsaturatedFattyAcids: fatUnsaturatedFattyAcids,
            carbohydrate: carbohydrate,
            carbohydratePureSugar: carbohydratePureSugar,
            fiber: fiber ?? 0,
            protein: protein,
            salt: salt
        )
    }
}
