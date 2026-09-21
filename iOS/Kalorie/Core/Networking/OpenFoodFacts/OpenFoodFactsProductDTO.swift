//
//  OpenFoodFactsProductDTO.swift
//  Kalorie
//
//  Created by Josef Antoni on 22.07.2026.
//

import Foundation
import MacroKit

struct OpenFoodFactsResponseDTO: Decodable {

    // MARK: - Properties

    let products: [OpenFoodFactsProductDTO]
}

struct OpenFoodFactsBarcodeResponseDTO: Decodable {

    // MARK: - Properties

    let status: Int
    let product: OpenFoodFactsProductDTO?
}

struct OpenFoodFactsProductDTO: Decodable {

    // MARK: - Properties

    let code: String
    let productNameCs: String?
    let productNameEn: String?
    let productName: String?
    let nutriments: OpenFoodFactsNutrimentsDTO?

    // MARK: - Coding keys

    enum CodingKeys: String, CodingKey {
        case code
        case productNameCs = "product_name_cs"
        case productNameEn = "product_name_en"
        case productName = "product_name"
        case nutriments
    }

    // MARK: - Functions

    func asDomain() -> FoodItemDomain? {
        // Reject products missing calories or name — a partial item would show 0 kcal in the UI,
        // which is worse than "not found". Callers treat nil as product not found.
        guard
            let nutriments,
            let kcal = nutriments.energyKcal100g,
            kcal > 0,
            let rawName = productNameCs ?? productNameEn ?? productName,
            !rawName.isEmpty
        else { return nil }
        let displayName = rawName.decodingHTMLEntities()
        let fat = nutriments.fat100g ?? 0
        let saturatedFat = nutriments.saturatedFat100g ?? 0
        let carbohydrate = nutriments.carbohydrates100g ?? 0
        let protein = nutriments.proteins100g ?? 0
        let rawOriginalName = productNameEn ?? productName ?? rawName
        return FoodItemDomain(
            id: code,
            kind: .external,
            czName: displayName,
            engName: rawOriginalName.decodingHTMLEntities(),
            weight: 100,
            date: .now,
            energyKJ: nutriments.energyKJ100g ?? MacrosKt.energyKJFromMacros(fat: fat, carbohydrate: carbohydrate, protein: protein),
            caloriesPerHundredGrams: kcal,
            fat: fat,
            fatSaturated: saturatedFat,
            fatUnsaturatedFattyAcids: max(0, fat - saturatedFat),
            carbohydrate: carbohydrate,
            carbohydratePureSugar: nutriments.sugars100g ?? 0,
            fiber: nutriments.fiber100g ?? 0,
            protein: protein,
            salt: nutriments.salt100g ?? 0
        )
    }
}

struct OpenFoodFactsNutrimentsDTO: Decodable {

    // MARK: - Properties

    let energyKcal100g: Double?
    let energyKJ100g: Double?
    let fat100g: Double?
    let saturatedFat100g: Double?
    let carbohydrates100g: Double?
    let sugars100g: Double?
    let fiber100g: Double?
    let proteins100g: Double?
    let salt100g: Double?

    // MARK: - Coding keys

    enum CodingKeys: String, CodingKey {
        case energyKcal100g = "energy-kcal_100g"
        case energyKJ100g = "energy_100g"
        case fat100g = "fat_100g"
        case saturatedFat100g = "saturated-fat_100g"
        case carbohydrates100g = "carbohydrates_100g"
        case sugars100g = "sugars_100g"
        case fiber100g = "fiber_100g"
        case proteins100g = "proteins_100g"
        case salt100g = "salt_100g"
    }
}
