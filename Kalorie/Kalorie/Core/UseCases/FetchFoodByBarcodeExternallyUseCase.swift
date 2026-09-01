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
        components.host = Constants.OpenFoodFacts.host
        components.percentEncodedPath = "/api/v2/product/\(encodedBarcode)"
        components.queryItems = [
            URLQueryItem(name: "fields", value: "code,product_name,product_name_cs,product_name_en,nutriments")
        ]
        guard let url = components.url else { throw FetchFoodByBarcodeExternallyError.invalidURL }
        // URLSession is used directly — OpenFoodFacts is plain HTTP, not Firestore, so FirestoreDataProviderProtocol doesn't apply.
        // The trade-off: fakes can only stub the return value, not assert which URL was called.
        let (data, _) = try await URLSession.shared.data(from: url)
        let response = try JSONDecoder().decode(OpenFoodFactsBarcodeResponseDTO.self, from: data)
        guard response.status == 1, let product = response.product else { return nil }
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
