//
//  MealType+CoreDataClass.swift
//  Kalorie
//
//  Created by Josef Antoni on 12.06.2024.
//
//

import Foundation
import CoreData


public class MealType: NSManagedObject {

    // MARK: - Init
    
    convenience init(
        id: Int,
        name: String,
        startTime: Date,
        endTime: Date,
        context: NSManagedObjectContext
    ) {
        self.init(context: context)

        self.id = id.toInt16
        self.name = name
        self.startTime = startTime.timeIntervalSince1970
        self.endTime = endTime.timeIntervalSince1970
    }
}
