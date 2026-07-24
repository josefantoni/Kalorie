//
//  FetchFoodByBarcodeExternallyUseCase.swift
//  Kalorie
//
//  Created by Josef Antoni on 24.07.2026.
//

import Foundation

enum FetchFoodByBarcodeExternallyError: Error {
    case invalidURL
}

protocol FetchFoodByBarcodeExternallyUseCaseProtocol {
    func callAsFunction(barcode: String) async throws -> FoodItemDomain?
}

struct FetchFoodByBarcodeExternallyUseCase: FetchFoodByBarcodeExternallyUseCaseProtocol {

    // MARK: - Functions

    func callAsFunction(barcode: String) async throws -> FoodItemDomain? {
        guard !barcode.isEmpty else { return nil }
        guard let encodedBarcode = barcode.addingPercentEncoding(withAllowedCharacters: .urlPathAllowed)
        else { throw FetchFoodByBarcodeExternallyError.invalidURL }
        var components = URLComponents()
        components.scheme = "https"
        components.host = "world.openfoodfacts.org"
        components.percentEncodedPath = "/api/v2/product/\(encodedBarcode)"
        components.queryItems = [
            URLQueryItem(name: "fields", value: "code,product_name,product_name_cs,product_name_en,nutriments")
        ]
        guard let url = components.url else { throw FetchFoodByBarcodeExternallyError.invalidURL }
        let (data, _) = try await URLSession.shared.data(from: url)
        let response = try JSONDecoder().decode(OpenFoodFactsBarcodeResponseDTO.self, from: data)
        guard response.status == 1, let product = response.product else { return nil }
        return mapToDomain(product)
    }

    // MARK: - Private

    private func mapToDomain(_ product: OpenFoodFactsProductDTO) -> FoodItemDomain? {
        guard
            let nutriments = product.nutriments,
            let kcal = nutriments.energyKcal100g,
            kcal > 0,
            let rawName = product.productNameCs ?? product.productNameEn ?? product.productName,
            !rawName.isEmpty
        else { return nil }
        let displayName = decodeHTMLEntities(rawName)
        let fat = nutriments.fat100g ?? 0
        let saturatedFat = nutriments.saturatedFat100g ?? 0
        let rawOriginalName = product.productNameEn ?? product.productName ?? rawName
        return FoodItemDomain(
            id: product.code,
            czName: displayName,
            engName: decodeHTMLEntities(rawOriginalName),
            weight: 100,
            date: .now,
            energyKJ: nutriments.energyKJ100g ?? 0,
            caloriesPerHundredGrams: kcal,
            fat: fat,
            fatSaturated: saturatedFat,
            fatUnsaturatedFattyAcids: max(0, fat - saturatedFat),
            carbohydrate: nutriments.carbohydrates100g ?? 0,
            carbohydratePureSugar: nutriments.sugars100g ?? 0,
            fiber: nutriments.fiber100g ?? 0,
            protein: nutriments.proteins100g ?? 0,
            salt: nutriments.salt100g ?? 0
        )
    }

    private func decodeHTMLEntities(_ string: String) -> String {
        var result = string
        let entities: [(String, String)] = [
            ("&amp;", "&"), ("&quot;", "\""), ("&lt;", "<"),
            ("&gt;", ">"), ("&apos;", "'"), ("&#39;", "'"), ("&nbsp;", " ")
        ]
        for (entity, replacement) in entities {
            result = result.replacingOccurrences(of: entity, with: replacement)
        }
        return result
    }
}

struct FetchFoodByBarcodeExternallyUseCaseFake: FetchFoodByBarcodeExternallyUseCaseProtocol {

    // MARK: - Properties

    var stubbedItem: FoodItemDomain?

    // MARK: - Functions

    func callAsFunction(barcode: String) async throws -> FoodItemDomain? { stubbedItem }
}
