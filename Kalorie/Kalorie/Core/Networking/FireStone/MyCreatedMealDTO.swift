//
//  MyCreatedMealDTO.swift
//  Kalorie
//
//  Created by Josef Antoni on 20.08.2026.
//

import Foundation
import MacroKit

struct MyCreatedMealIngredientDTO: Codable {

    // MARK: - Properties

    let foodItemId: String
    let czName: String
    let engName: String
    let grams: Double
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
        case grams, fat, carbohydrate, protein, salt, fiber
        case foodItemId = "food_item_id"
        case czName = "cz_name"
        case engName = "eng_name"
        case energyKJ = "energy_kj"
        case caloriesPerHundredGrams = "calories_per_hundred_grams"
        case fatSaturated = "fat_saturated"
        case fatUnsaturatedFattyAcids = "fat_unsaturated_fatty_acids"
        case carbohydratePureSugar = "carbohydrate_pure_sugar"
    }

    // MARK: - Init

    init(ingredient: MyCreatedMealIngredientDomain) {
        foodItemId = ingredient.foodItemId
        czName = ingredient.czName
        engName = ingredient.engName
        grams = ingredient.grams
        energyKJ = ingredient.nutrition.energyKJ
        caloriesPerHundredGrams = ingredient.nutrition.caloriesPerHundredGrams
        fat = ingredient.nutrition.fat
        fatSaturated = ingredient.nutrition.fatSaturated
        fatUnsaturatedFattyAcids = ingredient.nutrition.fatUnsaturatedFattyAcids
        carbohydrate = ingredient.nutrition.carbohydrate
        carbohydratePureSugar = ingredient.nutrition.carbohydratePureSugar
        fiber = ingredient.nutrition.fiber
        protein = ingredient.nutrition.protein
        salt = ingredient.nutrition.salt
    }

    // MARK: - Functions

    func asDomain() -> MyCreatedMealIngredientDomain {
        MyCreatedMealIngredientDomain(
            foodItemId: foodItemId,
            czName: czName,
            engName: engName,
            grams: grams,
            nutrition: FoodNutritionValues(
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
        )
    }
}

struct MyCreatedMealDTO: Codable {

    // MARK: - Properties

    let id: String
    let name: String
    let ingredients: [MyCreatedMealIngredientDTO]
    let createdAt: TimeInterval
    let updatedAt: TimeInterval

    // MARK: - Coding keys

    enum CodingKeys: String, CodingKey {
        case id, name, ingredients
        case createdAt = "created_at"
        case updatedAt = "updated_at"
    }

    // MARK: - Init

    init(meal: MyCreatedMealDomain) {
        id = meal.id
        name = meal.name
        ingredients = meal.ingredients.map(MyCreatedMealIngredientDTO.init(ingredient:))
        createdAt = meal.createdAt.timeIntervalSince1970
        updatedAt = meal.updatedAt.timeIntervalSince1970
    }

    // MARK: - Functions

    func asDomain() -> MyCreatedMealDomain {
        MyCreatedMealDomain(
            id: id,
            name: name,
            ingredients: ingredients.map { $0.asDomain() },
            createdAt: Date(timeIntervalSince1970: createdAt),
            updatedAt: Date(timeIntervalSince1970: updatedAt)
        )
    }
}
