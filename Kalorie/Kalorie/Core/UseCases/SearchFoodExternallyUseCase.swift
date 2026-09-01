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
        var components = URLComponents(string: "https://\(Constants.OpenFoodFacts.host)/cgi/search.pl")
        components?.queryItems = [
            URLQueryItem(name: "search_terms", value: query),
            URLQueryItem(name: "json", value: "1"),
            URLQueryItem(name: "page_size", value: "20"),
            URLQueryItem(name: "fields", value: "code,product_name,product_name_cs,product_name_en,nutriments")
        ]
        guard let url = components?.url else { throw SearchFoodExternallyError.invalidURL }
        let (data, _) = try await URLSession.shared.data(from: url)
        let response = try JSONDecoder().decode(OpenFoodFactsResponseDTO.self, from: data)
        return response.products.compactMap { $0.asDomain() }
    }

}

#if DEBUG
struct SearchFoodExternallyUseCaseFake: SearchFoodExternallyUseCaseProtocol {

    // MARK: - Properties

    var shouldThrow = false
    var stubbedItems: [FoodItemDomain] = []

    // MARK: - Functions

    func callAsFunction(query: String) async throws -> [FoodItemDomain] {
        if shouldThrow { throw SearchFoodExternallyError.invalidURL }
        return stubbedItems
    }
}
#endif
