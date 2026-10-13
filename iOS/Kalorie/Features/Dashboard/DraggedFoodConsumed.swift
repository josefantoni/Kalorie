//
//  DraggedFoodConsumed.swift
//  Kalorie
//
//  Created by Josef Antoni on 13.10.2026.
//

import UniformTypeIdentifiers

extension UTType {
    static let kalorieFoodConsumed = UTType(exportedAs: "antoni.Kalorie.food-consumed", conformingTo: .data)
}

struct DraggedFoodConsumed: Codable {

    // MARK: - Properties

    let id: String

    var itemProvider: NSItemProvider {
        let provider = NSItemProvider()
        provider.registerDataRepresentation(for: .kalorieFoodConsumed, visibility: .ownProcess) { completion in
            completion(try? JSONEncoder().encode(self), nil)
            return nil
        }
        return provider
    }

    // MARK: - Functions

    static func loadId(from providers: [NSItemProvider]) async -> String? {
        guard let provider = providers.first else { return nil }
        return await withCheckedContinuation { continuation in
            _ = provider.loadDataRepresentation(for: .kalorieFoodConsumed) { data, _ in
                let dragged = data.flatMap { try? JSONDecoder().decode(DraggedFoodConsumed.self, from: $0) }
                continuation.resume(returning: dragged?.id)
            }
        }
    }
}
