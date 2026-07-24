//
//  FirestoreDataProvider.swift
//  Kalorie
//
//  Created by Josef Antoni on 29.06.2026.
//

import Foundation
import FirebaseFirestore

protocol FirestoreDataProviderProtocol {
    func loadAsync<T: Decodable>(from collection: String) async throws -> [T]
    func loadAsync<T: Decodable>(from collection: String, where field: String, isGreaterThanOrEqualTo lowerBound: Double, isLessThan upperBound: Double) async throws -> [T]
    func loadAsync<T: Decodable>(from collection: String, where field: String, hasPrefix prefix: String, limit: Int) async throws -> [T]
    func saveAsync<T: Encodable>(_ item: T, to collection: String) async throws
    func setAsync<T: Encodable>(_ item: T, id: String, in collection: String) async throws
    func batchSetAsync<T: Encodable>(_ items: [(item: T, id: String)], in collection: String) async throws
    func deleteAsync(id: String, from collection: String) async throws
}

struct FirestoreDataProvider: FirestoreDataProviderProtocol {

    // MARK: - Functions

    func loadAsync<T: Decodable>(from collection: String) async throws -> [T] {
        log("🚀 GET \(collection)")
        do {
            let snapshot = try await Firestore.firestore().collection(collection).getDocuments()
            snapshot.documents.forEach { log("  [\($0.documentID)] \($0.data())") }
            let result: [T] = try snapshot.documents.compactMap { try $0.data(as: T.self) }
            log("✅ GET \(collection) → \(result.count) items")
            return result
        } catch {
            log("❌ GET \(collection): \(error)")
            throw error
        }
    }

    func loadAsync<T: Decodable>(from collection: String, where field: String, isGreaterThanOrEqualTo lowerBound: Double, isLessThan upperBound: Double) async throws -> [T] {
        log("🚀 GET \(collection) WHERE \(field) in [\(lowerBound), \(upperBound))")
        do {
            let snapshot = try await Firestore.firestore()
                .collection(collection)
                .whereField(field, isGreaterThanOrEqualTo: lowerBound)
                .whereField(field, isLessThan: upperBound)
                .getDocuments()
            let result: [T] = try snapshot.documents.compactMap { try $0.data(as: T.self) }
            log("✅ GET \(collection) → \(result.count) items")
            return result
        } catch {
            log("❌ GET \(collection): \(error)")
            throw error
        }
    }

    func loadAsync<T: Decodable>(from collection: String, where field: String, hasPrefix prefix: String, limit: Int) async throws -> [T] {
        log("🚀 GET \(collection) WHERE \(field) HASPREFIX '\(prefix)'")
        do {
            let snapshot = try await Firestore.firestore()
                .collection(collection)
                .whereField(field, isGreaterThanOrEqualTo: prefix)
                .whereField(field, isLessThan: prefix + "\u{f8ff}")
                .limit(to: limit)
                .getDocuments()
            let result: [T] = try snapshot.documents.compactMap { try $0.data(as: T.self) }
            log("✅ GET \(collection) → \(result.count) items")
            return result
        } catch {
            log("❌ GET \(collection): \(error)")
            throw error
        }
    }

    func saveAsync<T: Encodable>(_ item: T, to collection: String) async throws {
        do {
            let data = try Firestore.Encoder().encode(item)
            log("🚀 POST \(collection) body: \(data)")
            try await Firestore.firestore().collection(collection).addDocument(data: data)
            log("✅ POST \(collection)")
        } catch {
            log("❌ POST \(collection): \(error)")
            throw error
        }
    }

    func setAsync<T: Encodable>(_ item: T, id: String, in collection: String) async throws {
        do {
            let data = try Firestore.Encoder().encode(item)
            log("🚀 SET \(id) → \(collection) body: \(data)")
            try await Firestore.firestore().collection(collection).document(id).setData(data)
            log("✅ SET \(id) → \(collection)")
        } catch {
            log("❌ SET \(id) → \(collection): \(error)")
            throw error
        }
    }

    func batchSetAsync<T: Encodable>(_ items: [(item: T, id: String)], in collection: String) async throws {
        do {
            let db = Firestore.firestore()
            let batch = db.batch()
            for (item, id) in items {
                let data = try Firestore.Encoder().encode(item)
                log("🚀 BATCH SET \(id) → \(collection) body: \(data)")
                batch.setData(data, forDocument: db.collection(collection).document(id))
            }
            try await batch.commit()
            log("✅ BATCH SET \(items.count) items → \(collection)")
        } catch {
            log("❌ BATCH SET \(collection): \(error)")
            throw error
        }
    }

    func deleteAsync(id: String, from collection: String) async throws {
        log("🚀 DELETE \(id) from \(collection)")
        do {
            try await Firestore.firestore().collection(collection).document(id).delete()
            log("✅ DELETE \(id) from \(collection)")
        } catch {
            log("❌ DELETE \(id) from \(collection): \(error)")
            throw error
        }
    }
}

private func log(_ message: String) {
    #if DEBUG
    print(message)
    #endif
}
