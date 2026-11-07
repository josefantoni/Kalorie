//
//  PhotoSuggestionTestSupport.swift
//  KalorieTests
//
//  Created by Josef Antoni on 07.11.2026.
//

import Foundation
@testable import Kalorie

final class PhotoSuggestionDataProviderFake: FirestoreDataProviderProtocol {

    // MARK: - Properties

    var currentItem: FoodItemDomain?
    var stubbedSuggestionDocumentIds: [String] = []
    var stubbedSetError: Error?
    var stubbedLoadError: Error?
    var stubbedExistingSuggestions: [String: PhotoSuggestionDTO] = [:]
    var suggestionsAppearingAfterFailedSet: [String: PhotoSuggestionDTO] = [:]
    var stubbedCounts: [String: Int] = [:]
    var countError: Error?
    private(set) var setCalls: [(id: String, collection: String, item: Any)] = []
    private(set) var deletedIds: [(id: String, collection: String)] = []

    // MARK: - Functions

    func loadAsync<T: Decodable>(from collection: String) async throws -> [T] { [] }
    func loadFromServerAsync<T: Decodable>(from collection: String) async throws -> [T] { [] }
    func loadAsync<T: Decodable>(from collection: String, where field: String, isGreaterThanOrEqualTo lowerBound: Double, isLessThan upperBound: Double) async throws -> [T] { [] }
    func loadAsync<T: Decodable>(from collection: String, where field: String, hasPrefix prefix: String, limit: Int) async throws -> [T] { [] }
    func loadAsync<T: Decodable>(from collection: String, where field: String, arrayContains value: String, limit: Int) async throws -> [T] { [] }
    func loadAsync<T: Decodable>(from collection: String, where field: String, isEqualTo value: String) async throws -> T? { nil }
    func loadAsync<T: Decodable>(from collection: String, where field: String, isEqualTo value: String, orderBy orderField: String, descending: Bool) async throws -> [T] { [] }
    func loadAsync<T: Decodable>(id: String, from collection: String) async throws -> T? {
        guard collection == Constants.Firestore.foodItems, let currentItem else { return nil }
        return FoodItemDTO(item: currentItem) as? T
    }
    func loadFromServerAsync<T: Decodable>(id: String, from collection: String) async throws -> T? {
        if let stubbedLoadError { throw stubbedLoadError }
        if let stubbed = stubbedExistingSuggestions[id], collection == Constants.Firestore.photoSuggestions {
            return stubbed as? T
        }
        return setCalls.last { $0.id == id && $0.collection == collection }?.item as? T
    }
    func loadAsync<T: Decodable>(from collection: String, orderBy field: String, descending: Bool, limit: Int) async throws -> [T] { [] }
    func saveAsync<T: Encodable>(_ item: T, to collection: String) async throws {}
    func setAsync<T: Encodable>(_ item: T, id: String, in collection: String) async throws {
        if let stubbedSetError {
            stubbedExistingSuggestions.merge(suggestionsAppearingAfterFailedSet) { _, new in new }
            throw stubbedSetError
        }
        setCalls.append((id, collection, item))
    }
    func batchSetAsync<T: Encodable>(_ items: [(item: T, id: String)], in collection: String) async throws {}
    func deleteAsync(id: String, from collection: String) async throws {
        deletedIds.append((id, collection))
    }
    func batchDeleteAsync(ids: [String], from collection: String) async throws {}
    func loadDocumentIdsAsync(from collection: String) async throws -> [String] { [] }
    func loadDocumentIdsAsync(from collection: String, where field: String, isEqualTo value: String) async throws -> [String] {
        collection == Constants.Firestore.photoSuggestions ? stubbedSuggestionDocumentIds : []
    }
    func incrementEntryAsync<T: Encodable>(_ item: T, entryId: String, lastLoggedAt: TimeInterval, documentId: String, in collection: String) async throws {}
    func deleteEntriesAsync(ids: [String], documentId: String, in collection: String) async throws {}
    func countAsync(from collection: String) async throws -> Int {
        if let countError { throw countError }
        return stubbedCounts[collection] ?? 0
    }
    func countAsync(from collection: String, where field: String, isEqualTo value: String) async throws -> Int {
        if let countError { throw countError }
        return stubbedCounts[collection] ?? 0
    }
}

func makePhotoSuggestionItem(id: String = "12345678", photoURL: URL? = nil, caloriesPerHundredGrams: Double = 80) -> FoodItemDomain {
    FoodItemDomain(
        id: id,
        kind: .catalogue,
        czName: "Tvaroh",
        engName: "",
        weight: 200,
        date: Date(timeIntervalSince1970: 1_700_000_000),
        energyKJ: 335,
        caloriesPerHundredGrams: caloriesPerHundredGrams,
        fat: 0.5,
        fatSaturated: 0.3,
        fatUnsaturatedFattyAcids: 0.2,
        carbohydrate: 4,
        carbohydratePureSugar: 3,
        fiber: 0,
        protein: 13,
        salt: 0.1,
        photoURL: photoURL
    )
}
