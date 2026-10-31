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
    let imageFrontUrl: String?
    let servingQuantity: LenientServingQuantity?
    let servingQuantityUnit: LenientString?

    // MARK: - Coding keys

    enum CodingKeys: String, CodingKey {
        case code
        case servingQuantity = "serving_quantity"
        case servingQuantityUnit = "serving_quantity_unit"
        case imageFrontUrl = "image_front_url"
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
            let rawName = firstNonEmpty(productNameCs, productNameEn, productName)
        else { return nil }
        let displayName = rawName.decodingHTMLEntities()
        let fat = nutriments.fat100g ?? 0
        let saturatedFat = nutriments.saturatedFat100g ?? 0
        let carbohydrate = nutriments.carbohydrates100g ?? 0
        let protein = nutriments.proteins100g ?? 0
        let rawOriginalName = firstNonEmpty(productNameEn, productName) ?? rawName
        let unit = servingQuantityUnit?.value?.trimmingCharacters(in: Constants.OpenFoodFacts.servingQuantityWhitespace).lowercased()
        let validServing = validServingQuantity(unit: unit)
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
            salt: nutriments.salt100g ?? 0,
            portions: servingPortions(validServing: validServing),
            measure: validServing != nil && unit == Constants.OpenFoodFacts.millilitresUnit ? .millilitres : .grams,
            photoURL: imageFrontUrl.flatMap(URL.init(string:))
        )
    }

    private func servingPortions(validServing: Double?) -> [FoodPortionDomain] {
        guard
            let validServing,
            validServing != Constants.OpenFoodFacts.perHundredAmount
        else { return [] }
        return [FoodPortionDomain(name: Constants.OpenFoodFacts.servingPortionName, grams: validServing)]
    }

    private func validServingQuantity(unit: String?) -> Double? {
        guard
            unit == Constants.OpenFoodFacts.gramsUnit || unit == Constants.OpenFoodFacts.millilitresUnit,
            let servingQuantity = servingQuantity?.value,
            (servingQuantity * 10).isFinite
        else { return nil }
        let rounded = (servingQuantity * 10).rounded() / 10
        guard
            FoodPortionValidation.validate(name: Constants.OpenFoodFacts.servingPortionName, grams: rounded) == nil,
            rounded <= Constants.OpenFoodFacts.maxServingQuantity
        else { return nil }
        return rounded
    }

    private func firstNonEmpty(_ names: String?...) -> String? {
        names
            .compactMap { $0 }
            .first { !$0.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }
    }
}

extension OpenFoodFactsProductDTO {

    struct LenientString: Decodable {

        // MARK: - Properties

        let value: String?

        // MARK: - Init

        init(from decoder: Decoder) throws {
            value = try? decoder.singleValueContainer().decode(String.self)
        }
    }

    struct LenientServingQuantity: Decodable {

        // MARK: - Properties

        let value: Double?

        // MARK: - Init

        init(from decoder: Decoder) throws {
            let container = try decoder.singleValueContainer()
            if let number = try? container.decode(Double.self) {
                value = number
            } else if let text = try? container.decode(String.self) {
                let trimmed = text.trimmingCharacters(in: Constants.OpenFoodFacts.servingQuantityWhitespace)
                value = trimmed.wholeMatch(of: Self.plainDecimal) != nil ? Double(trimmed) : nil
            } else {
                value = nil
            }
        }

        private static let plainDecimal = #/[0-9]+(\.[0-9]+)?/#
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
