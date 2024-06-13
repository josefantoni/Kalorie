//
//  FoodItem+CoreDataClass.swift
//  Kalorie
//
//  Created by Josef Antoni on 12.06.2024.
//
//

import Foundation
import CoreData

public class FoodItem: NSManagedObject {

    var timeFormatted: String {
        Date(timeIntervalSince1970: date).formatDateStyle(with: "HH:mm")
    }
    
    var caloriesFormatted: String {
        "\(self.calories) kcal"
    }
}
