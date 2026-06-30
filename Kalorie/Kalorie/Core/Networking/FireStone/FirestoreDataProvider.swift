//
//  FirestoreDataProvider.swift
//  Kalorie
//
//  Created by Josef Antoni on 29.06.2026.
//

import Foundation
import FirebaseFirestore

protocol FirestoreDataProviderProtocol {
    func loadAsync<T: Decodable>(_ collection: String) async throws -> [T]
    func saveAsync<T: Encodable>(_ item: T, to collection: String) async throws
}

struct FirestoreDataProvider: FirestoreDataProviderProtocol {

    // MARK: - Functions

    func loadAsync<T: Decodable>(_ collection: String) async throws -> [T] {
        let snapshot = try await Firestore.firestore().collection(collection).getDocuments()
        return try snapshot.documents.compactMap { try $0.data(as: T.self) }
    }

    func saveAsync<T: Encodable>(_ item: T, to collection: String) async throws {
        let data = try Firestore.Encoder().encode(item)
        try await Firestore.firestore().collection(collection).addDocument(data: data)
    }
}
