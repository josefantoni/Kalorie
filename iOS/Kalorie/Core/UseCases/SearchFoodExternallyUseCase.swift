//
//  SearchFoodExternallyUseCase.swift
//  Kalorie
//
//  Created by Josef Antoni on 22.07.2026.
//

import Foundation

enum SearchFoodExternallyError: Error {
    case invalidURL
    case serverError(statusCode: Int)
}

protocol SearchFoodExternallyUseCaseProtocol {
    func callAsFunction(query: String) async throws -> [FoodItemDomain]
}

struct SearchFoodExternallyUseCase: SearchFoodExternallyUseCaseProtocol {

    // MARK: - Properties

    private let session: URLSession
    private let retryDelay: Duration

    // MARK: - Init

    init(session: URLSession = .shared, retryDelay: Duration = Constants.OpenFoodFacts.retryDelay) {
        self.session = session
        self.retryDelay = retryDelay
    }

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
        var request = URLRequest(url: url, timeoutInterval: Constants.OpenFoodFacts.requestTimeout)
        request.setValue(Constants.OpenFoodFacts.userAgent, forHTTPHeaderField: "User-Agent")
        let (data, statusCode) = try await OpenFoodFactsTransientRequest.data(for: request, session: session, retryDelay: retryDelay)
        guard (200...299).contains(statusCode) else {
            throw SearchFoodExternallyError.serverError(statusCode: statusCode)
        }
        let decoded = try JSONDecoder().decode(OpenFoodFactsResponseDTO.self, from: data)
        return decoded.products.compactMap { $0.asDomain() }
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
