//
//  FoodItemDTO.swift
//  Kalorie
//
//  Created by Josef Antoni on 26.06.2024.
//

import Foundation


public struct FoodItemDTO: Codable {
    
    // MARK: - Properties
    
    var id: String
    var name: String
    var weight: Double
    var date: TimeInterval
    var caloriesPerHundredGrams: Double
    var fat: Double
    var fatUnsaturatedFattyAcids: Double
    var carbohydrate: Double
    var carbohydratePureSugar: Double
    var protein: Double
    var salt: Double
    
    var dictionary: [String: Any] {
        let data = (try? JSONEncoder().encode(self)) ?? Data()
        return (try? JSONSerialization.jsonObject(with: data, options: .mutableContainers) as? [String: Any]) ?? [:]
    }


    // MARK: - Coding keys
    
    enum CodingKeys: String, CodingKey {
        case id, name, weight, date, fat, carbohydrate, protein, salt
        case caloriesPerHundredGrams = "calories_per_hundred_grams"
        case fatUnsaturatedFattyAcids = "fat_unsaturated_fatty_acids"
        case carbohydratePureSugar = "carbohydrate_pure_sugar"
    }
}
