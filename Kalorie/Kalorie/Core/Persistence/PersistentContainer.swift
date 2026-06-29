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

    static let shared: NSPersistentContainer = {
        let container = NSPersistentContainer(name: "Model")
        container.loadPersistentStores { _, error in
            if let error = error as NSError? {
                fatalError("Error loading data: \(error)")
            }
        }
        return container
    }()
}
