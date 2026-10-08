//
//  FoodItemFormPhoto.swift
//  Kalorie
//
//  Created by Josef Antoni on 08.10.2026.
//

import Foundation

enum FoodItemFormPhoto: Equatable {
    case none
    case remote(URL)
    case local(Data)
}
