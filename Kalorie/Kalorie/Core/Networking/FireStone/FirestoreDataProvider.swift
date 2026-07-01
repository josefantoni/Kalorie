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
    func saveAsync<T: Encodable>(_ item: T, to collection: String) async throws
    func setAsync<T: Encodable>(_ item: T, id: String, in collection: String) async throws
    func batchSetAsync<T: Encodable>(_ items: [(item: T, id: String)], in collection: String) async throws
    func deleteAsync(id: String, from collection: String) async throws
}

struct FirestoreDataProvider: FirestoreDataProviderProtocol {

    // MARK: - Functions

    func loadAsync<T: Decodable>(from collection: String) async throws -> [T] {
        let snapshot = try await Firestore.firestore().collection(collection).getDocuments()
        return try snapshot.documents.compactMap { try $0.data(as: T.self) }
    }

    func saveAsync<T: Encodable>(_ item: T, to collection: String) async throws {
        let data = try Firestore.Encoder().encode(item)
        try await Firestore.firestore().collection(collection).addDocument(data: data)
    }

    func setAsync<T: Encodable>(_ item: T, id: String, in collection: String) async throws {
        let data = try Firestore.Encoder().encode(item)
        try await Firestore.firestore().collection(collection).document(id).setData(data)
    }

    func batchSetAsync<T: Encodable>(_ items: [(item: T, id: String)], in collection: String) async throws {
        let db = Firestore.firestore()
        let batch = db.batch()
        for (item, id) in items {
            let data = try Firestore.Encoder().encode(item)
            batch.setData(data, forDocument: db.collection(collection).document(id))
        }
        try await batch.commit()
    }

    func deleteAsync(id: String, from collection: String) async throws {
        try await Firestore.firestore().collection(collection).document(id).delete()
    }
}
