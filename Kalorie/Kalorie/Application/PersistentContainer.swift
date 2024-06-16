//
//  PersistentContainer.swift
//  Kalorie
//
//  Created by Josef Antoni on 12.06.2024.
//

import Foundation
import CoreData


enum PersistentContainer {
    
    static var container: NSPersistentContainer {
        let container = NSPersistentContainer(name: "Model")
        container.loadPersistentStores { _, error in
            if let error = error as NSError? {
                // we can migrate in later phase of project
                fatalError("Error loading data: \(error)")
            }
        }
        return container
    }
    
    
    static func save(container: NSPersistentContainer) {
        do {
            try container.viewContext.save()
            print("Save was sucessfull")
        } catch let error {
            fatalError("Something went wrong white saving to DB:  \(error)")
        }
    }
}
