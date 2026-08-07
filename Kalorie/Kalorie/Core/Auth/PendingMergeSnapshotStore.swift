//
//  PendingMergeSnapshotStore.swift
//  Kalorie
//
//  Created by Josef Antoni on 07.08.2026.
//

import Foundation

struct PendingMergeSnapshot: Codable {
    let sourceAnonymousUserId: String
    let foodConsumed: [FoodConsumedDTO]
}

protocol PendingMergeSnapshotStoreProtocol {
    func save(_ snapshot: PendingMergeSnapshot) throws
    func load() throws -> PendingMergeSnapshot?
    func delete() throws
}

struct PendingMergeSnapshotStore: PendingMergeSnapshotStoreProtocol {

    // MARK: - Properties

    private var fileURL: URL {
        FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("pending-merge.json")
    }

    // MARK: - Functions

    func save(_ snapshot: PendingMergeSnapshot) throws {
        try JSONEncoder().encode(snapshot).write(to: fileURL, options: .atomic)
    }

    func load() throws -> PendingMergeSnapshot? {
        guard FileManager.default.fileExists(atPath: fileURL.path) else { return nil }
        return try JSONDecoder().decode(PendingMergeSnapshot.self, from: Data(contentsOf: fileURL))
    }

    func delete() throws {
        guard FileManager.default.fileExists(atPath: fileURL.path) else { return }
        try FileManager.default.removeItem(at: fileURL)
    }
}

#if DEBUG
final class PendingMergeSnapshotStoreFake: PendingMergeSnapshotStoreProtocol {

    // MARK: - Properties

    var stubbedSnapshot: PendingMergeSnapshot?
    var saveError: Error?
    private(set) var deleteCallCount = 0

    // MARK: - Functions

    func save(_ snapshot: PendingMergeSnapshot) throws {
        if let saveError { throw saveError }
        stubbedSnapshot = snapshot
    }

    func load() throws -> PendingMergeSnapshot? {
        stubbedSnapshot
    }

    func delete() throws {
        deleteCallCount += 1
        stubbedSnapshot = nil
    }
}
#endif
