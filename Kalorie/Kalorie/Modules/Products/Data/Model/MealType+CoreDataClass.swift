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
        id: UUID,
        name: String,
        start: (Int, Int),
        end: (Int, Int),
        context: NSManagedObjectContext
    ) {
        self.init(context: context)

        self.id = id
        self.name = name
        self.startHour = start.0.toInt16
        self.startMinute = start.1.toInt16
        self.endHour = end.0.toInt16
        self.endMinute = end.1.toInt16
    }
}
