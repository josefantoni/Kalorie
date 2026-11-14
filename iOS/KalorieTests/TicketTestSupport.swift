//
//  TicketTestSupport.swift
//  KalorieTests
//
//  Created by Josef Antoni on 14.11.2026.
//

import Foundation
@testable import Kalorie

final class TicketDataProviderFake: FirestoreDataProviderProtocol {

    // MARK: - Types

    struct Write {
        let id: String
        let collection: String
        let item: Any
    }

    struct OrderedQuery: Hashable {
        let collection: String
        let field: String
        let value: String
        let boolField: String?
        let boolValue: Bool?
        let orderField: String
        let descending: Bool
    }

    // MARK: - Properties

    var currentItem: FoodItemDomain?
    var stubbedReports: [FoodItemReportDTO] = []
    var stubbedSuggestions: [PhotoSuggestionDTO] = []
    var stubbedDocumentIds: [String: [String]] = [:]
    var stubbedCounts: [String: Int] = [:]
    var failingWriteCollections: Set<String> = []
    var failingError: Error = URLError(.notConnectedToInternet)
    var failingLoadCollections: Set<String> = []
    var newIds = ["ticket-1", "ticket-2", "ticket-3"]
    private(set) var sets: [Write] = []
    private(set) var updates: [Write] = []
    private(set) var deletes: [(id: String, collection: String)] = []
    private(set) var anonymised: [(id: String, ownerField: String, collection: String)] = []
    private(set) var orderedQueries: [OrderedQuery] = []
    private(set) var equalityQueries: [(collection: String, filters: [String], limit: Int)] = []
    private(set) var events: [String] = []

    // MARK: - Functions

    func loadAsync<T: Decodable>(from collection: String) async throws -> [T] { [] }
    func loadFromServerAsync<T: Decodable>(from collection: String) async throws -> [T] { [] }
    func loadAsync<T: Decodable>(from collection: String, where field: String, isGreaterThanOrEqualTo lowerBound: Double, isLessThan upperBound: Double) async throws -> [T] { [] }
    func loadAsync<T: Decodable>(from collection: String, where field: String, hasPrefix prefix: String, limit: Int) async throws -> [T] { [] }
    func loadAsync<T: Decodable>(from collection: String, where field: String, arrayContains value: String, limit: Int) async throws -> [T] { [] }
    func loadAsync<T: Decodable>(from collection: String, where field: String, isEqualTo value: String) async throws -> T? { nil }
    func loadAsync<T: Decodable>(from collection: String, orderBy field: String, descending: Bool, limit: Int) async throws -> [T] { [] }
    func loadFromServerAsync<T: Decodable>(id: String, from collection: String) async throws -> T? { nil }
    func saveAsync<T: Encodable>(_ item: T, to collection: String) async throws {}
    func batchSetAsync<T: Encodable>(_ items: [(item: T, id: String)], in collection: String) async throws {}

    func loadAsync<T: Decodable>(from collection: String, where field: String, isEqualTo value: String, orderBy orderField: String, descending: Bool) async throws -> [T] {
        orderedQueries.append(OrderedQuery(collection: collection, field: field, value: value, boolField: nil, boolValue: nil, orderField: orderField, descending: descending))
        return try stubbed(collection)
    }

    func loadAsync<T: Decodable>(from collection: String, whereEqualTo filters: [(String, String)], isHidden: Bool, orderBy orderField: String, descending: Bool) async throws -> [T] {
        let filter = filters.first ?? ("", "")
        orderedQueries.append(OrderedQuery(collection: collection, field: filter.0, value: filter.1, boolField: "hidden", boolValue: isHidden, orderField: orderField, descending: descending))
        return try stubbed(collection)
    }

    func loadAsync<T: Decodable>(from collection: String, whereEqualTo filters: [(String, String)], limit: Int) async throws -> [T] {
        equalityQueries.append((collection, filters.map { "\($0.0)==\($0.1)" }, limit))
        return try stubbed(collection)
    }

    func loadAsync<T: Decodable>(id: String, from collection: String) async throws -> T? {
        guard collection == Constants.Firestore.foodItems, let currentItem else { return nil }
        return FoodItemDTO(item: currentItem) as? T
    }

    func newDocumentId(in collection: String) throws -> String {
        guard !newIds.isEmpty else { return UUID().uuidString }
        return newIds.removeFirst()
    }

    func setAsync<T: Encodable>(_ item: T, id: String, in collection: String) async throws {
        if failingWriteCollections.contains(collection) { throw failingError }
        sets.append(Write(id: id, collection: collection, item: item))
        events.append("set \(collection)/\(id)")
    }

    func updateAsync<T: Encodable>(_ item: T, id: String, in collection: String) async throws {
        if failingWriteCollections.contains(collection) { throw failingError }
        updates.append(Write(id: id, collection: collection, item: item))
        events.append("update \(collection)/\(id)")
    }

    func deleteAsync(id: String, from collection: String) async throws {
        if failingWriteCollections.contains(collection) { throw failingError }
        deletes.append((id, collection))
        events.append("delete \(collection)/\(id)")
    }

    func anonymiseAsync(id: String, ownerField: String, in collection: String) async throws {
        anonymised.append((id, ownerField, collection))
    }

    func loadDocumentIdsAsync(from collection: String, where field: String, isEqualTo value: String) async throws -> [String] {
        stubbedDocumentIds[collection] ?? []
    }

    func countAsync(from collection: String, where field: String, isEqualTo value: String) async throws -> Int {
        stubbedCounts[collection] ?? 0
    }

    // MARK: - Private

    private func stubbed<T>(_ collection: String) throws -> [T] {
        if failingLoadCollections.contains(collection) { throw failingError }
        switch collection {
        case Constants.Firestore.foodItemReports: return stubbedReports as? [T] ?? []
        case Constants.Firestore.photoSuggestions: return stubbedSuggestions as? [T] ?? []
        default: return []
        }
    }
}

func makeReportDTO(
    id: String = "report-1",
    barcode: String = "12345678",
    reportedBy: String? = "user-1",
    reportedAt: Date = Date(timeIntervalSince1970: 1_700_000_000),
    status: TicketStatus = .pending,
    locale: String? = "en",
    response: String? = nil,
    resolvedAt: Date? = nil,
    seenAt: Date? = nil
) -> FoodItemReportDTO {
    FoodItemReportDTO(
        id: id,
        barcode: barcode,
        reportedBy: reportedBy,
        reason: "wrong fat",
        reportedAt: reportedAt.timeIntervalSince1970,
        status: status,
        locale: locale,
        response: response,
        resolvedAt: resolvedAt?.timeIntervalSince1970,
        seenAt: seenAt?.timeIntervalSince1970
    )
}

func makeSuggestionDTO(
    id: String = "photo-1",
    itemId: String = "12345678",
    suggestedBy: String? = "user-1",
    suggestedAt: Date = Date(timeIntervalSince1970: 1_700_000_000),
    status: TicketStatus = .pending,
    locale: String? = "en",
    response: String? = nil,
    resolvedAt: Date? = nil,
    seenAt: Date? = nil
) -> PhotoSuggestionDTO {
    PhotoSuggestionDTO(
        id: id,
        itemId: itemId,
        suggestedBy: suggestedBy,
        photoUrl: "https://example.com/\(itemId)/\(id).jpg",
        suggestedAt: suggestedAt.timeIntervalSince1970,
        status: status,
        locale: locale,
        response: response,
        resolvedAt: resolvedAt?.timeIntervalSince1970,
        seenAt: seenAt?.timeIntervalSince1970
    )
}

func makeReport(id: String = "report-1", barcode: String = "12345678", locale: String = "en") -> FoodItemReportDomain {
    makeReportDTO(id: id, barcode: barcode, locale: locale).asDomain() ?? { fatalError("DTO with an id always maps") }()
}

func makeSuggestion(id: String = "photo-1", itemId: String = "12345678", locale: String = "en") -> PhotoSuggestionDomain {
    makeSuggestionDTO(id: id, itemId: itemId, locale: locale).asDomain() ?? { fatalError("DTO with an id always maps") }()
}

func makeNotification(
    kind: UserNotificationDomain.Kind = .report,
    id: String = "ticket-1",
    itemId: String = "12345678",
    createdAt: Date = Date(timeIntervalSince1970: 1_700_000_000),
    status: TicketStatus = .pending,
    resolvedAt: Date? = nil,
    seenAt: Date? = nil
) -> UserNotificationDomain {
    UserNotificationDomain(
        kind: kind,
        id: id,
        itemId: itemId,
        createdAt: createdAt,
        status: status,
        resolvedAt: resolvedAt,
        seenAt: seenAt,
        photoURL: kind == .photoSuggestion ? URL(string: "https://example.com/\(itemId)/\(id).jpg") : nil
    )
}
