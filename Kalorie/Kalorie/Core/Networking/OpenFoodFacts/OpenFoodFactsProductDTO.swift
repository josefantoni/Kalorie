//
//  OpenFoodFactsProductDTO.swift
//  Kalorie
//
//  Created by Josef Antoni on 22.07.2026.
//

import Foundation

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
