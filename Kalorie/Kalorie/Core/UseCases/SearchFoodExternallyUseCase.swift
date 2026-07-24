//
//  SearchFoodExternallyUseCase.swift
//  Kalorie
//
//  Created by Josef Antoni on 22.07.2026.
//

import Foundation

enum SearchFoodExternallyError: Error {
    case invalidURL
}

protocol SearchFoodExternallyUseCaseProtocol {
    func callAsFunction(query: String) async throws -> [FoodItemDomain]
}

struct SearchFoodExternallyUseCase: SearchFoodExternallyUseCaseProtocol {

    // MARK: - Functions

    func callAsFunction(query: String) async throws -> [FoodItemDomain] {
        var components = URLComponents(string: "https://world.openfoodfacts.org/cgi/search.pl")
        components?.queryItems = [
            URLQueryItem(name: "search_terms", value: query),
            URLQueryItem(name: "json", value: "1"),
            URLQueryItem(name: "page_size", value: "20"),
            URLQueryItem(name: "fields", value: "code,product_name,product_name_cs,product_name_en,nutriments")
        ]
        guard let url = components?.url else { throw SearchFoodExternallyError.invalidURL }
        let (data, _) = try await URLSession.shared.data(from: url)
        let response = try JSONDecoder().decode(OpenFoodFactsResponseDTO.self, from: data)
        return response.products.compactMap(mapToDomain)
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

struct SearchFoodExternallyUseCaseFake: SearchFoodExternallyUseCaseProtocol {

    // MARK: - Functions

    func callAsFunction(query: String) async throws -> [FoodItemDomain] { [] }
}
