//
//  NutritionLabelReading.swift
//  Kalorie
//
//  Created by Josef Antoni on 13.09.2026.
//

import Foundation

struct NutritionLabelReading: Equatable {

    // MARK: - Properties

    var scannedCode: String?
    var name: String?
    var weightOfProduct: Double?
    var energyKJ: Double?
    var caloriesPerHundredGrams: Double?
    var fat: Double?
    var fatSaturated: Double?
    var fatUnsaturatedFattyAcids: Double?
    var carbohydrate: Double?
    var carbohydratePureSugar: Double?
    var fiber: Double?
    var protein: Double?
    var salt: Double?
    var portions: [FoodPortionDomain]?

    // MARK: - Init

    init(
        scannedCode: String? = nil,
        name: String? = nil,
        weightOfProduct: Double? = nil,
        energyKJ: Double? = nil,
        caloriesPerHundredGrams: Double? = nil,
        fat: Double? = nil,
        fatSaturated: Double? = nil,
        fatUnsaturatedFattyAcids: Double? = nil,
        carbohydrate: Double? = nil,
        carbohydratePureSugar: Double? = nil,
        fiber: Double? = nil,
        protein: Double? = nil,
        salt: Double? = nil,
        portions: [FoodPortionDomain]? = nil
    ) {
        self.scannedCode = scannedCode
        self.name = name
        self.weightOfProduct = weightOfProduct
        self.energyKJ = energyKJ
        self.caloriesPerHundredGrams = caloriesPerHundredGrams
        self.fat = fat
        self.fatSaturated = fatSaturated
        self.fatUnsaturatedFattyAcids = fatUnsaturatedFattyAcids
        self.carbohydrate = carbohydrate
        self.carbohydratePureSugar = carbohydratePureSugar
        self.fiber = fiber
        self.protein = protein
        self.salt = salt
        self.portions = portions
    }
}

extension NutritionLabelReading {

    // MARK: - Properties

    var recognizedFields: Set<FoodItemFormField> {
        var fields: Set<FoodItemFormField> = []
        if name != nil { fields.insert(.name) }
        if weightOfProduct != nil { fields.insert(.weight) }
        if energyKJ != nil { fields.insert(.energyKJ) }
        if caloriesPerHundredGrams != nil { fields.insert(.calories) }
        if fat != nil { fields.insert(.fat) }
        if fatSaturated != nil { fields.insert(.fatSaturated) }
        if fatUnsaturatedFattyAcids != nil { fields.insert(.fatUnsaturated) }
        if carbohydrate != nil { fields.insert(.carbohydrate) }
        if carbohydratePureSugar != nil { fields.insert(.carbohydrateSugar) }
        if fiber != nil { fields.insert(.fiber) }
        if protein != nil { fields.insert(.protein) }
        if salt != nil { fields.insert(.salt) }
        return fields
    }

    var isEmpty: Bool {
        recognizedFields.isEmpty && scannedCode == nil && (portions?.isEmpty ?? true)
    }

    var isCompleteForAutoCapture: Bool {
        caloriesPerHundredGrams != nil && fat != nil && carbohydrate != nil && protein != nil
    }
}
