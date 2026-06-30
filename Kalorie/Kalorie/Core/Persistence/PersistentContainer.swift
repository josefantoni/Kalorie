//
//  PersistentContainer.swift
//  Kalorie
//
//  Created by Josef Antoni on 12.06.2024.
//

import Foundation
import CoreData

enum PersistentContainer {

    // MARK: - Properties

    static let shared: NSPersistentContainer = NSPersistentContainer(name: Constants.CoreData.modelName)

    // MARK: - Functions

    static func load() async {
        await withCheckedContinuation { continuation in
            Task.detached(priority: .userInitiated) {
                shared.loadPersistentStores { _, error in
                    if let error = error as NSError? {
                        fatalError("Error loading data: \(error)")
                    }
                    continuation.resume()
                }
            }
        }
    }
}
