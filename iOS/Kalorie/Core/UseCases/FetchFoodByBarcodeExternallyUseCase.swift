//
//  FetchFoodByBarcodeExternallyUseCase.swift
//  Kalorie
//
//  Created by Josef Antoni on 24.07.2026.
//

import Foundation

enum FetchFoodByBarcodeExternallyError: Error {
    case invalidURL
    case serverError(statusCode: Int)
}

protocol FetchFoodByBarcodeExternallyUseCaseProtocol {
    func callAsFunction(barcode: String) async throws -> FoodItemDomain?
}

struct FetchFoodByBarcodeExternallyUseCase: FetchFoodByBarcodeExternallyUseCaseProtocol {

    // MARK: - Properties

    private let session: URLSession
    private let retryDelay: Duration

    // MARK: - Init

    init(session: URLSession = .shared, retryDelay: Duration = Constants.OpenFoodFacts.retryDelay) {
        self.session = session
        self.retryDelay = retryDelay
    }

    // MARK: - Functions

    func callAsFunction(barcode: String) async throws -> FoodItemDomain? {
        guard !barcode.isEmpty else { return nil }
        guard let encodedBarcode = barcode.addingPercentEncoding(withAllowedCharacters: .urlPathAllowed)
        else { throw FetchFoodByBarcodeExternallyError.invalidURL }
        var components = URLComponents()
        components.scheme = "https"
        components.host = Constants.OpenFoodFacts.host
        components.percentEncodedPath = "/api/v2/product/\(encodedBarcode)"
        components.queryItems = [
            URLQueryItem(name: "fields", value: "code,product_name,product_name_cs,product_name_en,nutriments")
        ]
        guard let url = components.url else { throw FetchFoodByBarcodeExternallyError.invalidURL }
        // URLSession is used directly — OpenFoodFacts is plain HTTP, not Firestore, so FirestoreDataProviderProtocol doesn't apply.
        // The trade-off: fakes can only stub the return value, not assert which URL was called.
        var request = URLRequest(url: url, timeoutInterval: Constants.OpenFoodFacts.requestTimeout)
        request.setValue(Constants.OpenFoodFacts.userAgent, forHTTPHeaderField: "User-Agent")
        let (data, statusCode) = try await OpenFoodFactsTransientRequest.data(for: request, session: session, retryDelay: retryDelay)
        guard (200...299).contains(statusCode) else {
            throw FetchFoodByBarcodeExternallyError.serverError(statusCode: statusCode)
        }
        let decoded = try JSONDecoder().decode(OpenFoodFactsBarcodeResponseDTO.self, from: data)
        guard decoded.status == 1, let product = decoded.product else { return nil }
        return product.asDomain()
    }

}

#if DEBUG
struct FetchFoodByBarcodeExternallyUseCaseFake: FetchFoodByBarcodeExternallyUseCaseProtocol {

    // MARK: - Properties

    var stubbedItem: FoodItemDomain?
    var shouldThrow = false

    // MARK: - Functions

    func callAsFunction(barcode: String) async throws -> FoodItemDomain? {
        if shouldThrow { throw FetchFoodByBarcodeExternallyError.invalidURL }
        return stubbedItem
    }
}
#endif
