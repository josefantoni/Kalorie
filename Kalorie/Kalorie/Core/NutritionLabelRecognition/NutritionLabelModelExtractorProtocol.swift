//
//  NutritionLabelModelExtractorProtocol.swift
//  Kalorie
//
//  Created by Josef Antoni on 13.09.2026.
//

import Foundation
import FoundationModels

@Generable
struct NutritionLabelPortionCandidate {
    @Guide(description: "Name of a named portion printed on the packaging, e.g. '1 slice' or '1 balení'") var name: String
    @Guide(description: "Weight of this portion in grams") var grams: Double
}

@Generable
struct NutritionLabelModelCandidate {
    @Guide(description: "The product's name exactly as printed on the packaging") var name: String?
    @Guide(description: "The total package weight in grams, e.g. 250 for '250 g'. Nil if not printed.") var packageWeightGrams: Double?
    @Guide(description: "Named portion sizes with their gram amount, if any are printed on the packaging") var portions: [NutritionLabelPortionCandidate]?
    @Guide(description: "Energy in kJ per 100 g, from the nutrition table's 100 g column") var energyKJPer100g: Double?
    @Guide(description: "Energy in kcal per 100 g, from the nutrition table's 100 g column") var caloriesPer100g: Double?
    @Guide(description: "Fat in grams per 100 g") var fatPer100g: Double?
    @Guide(description: "Saturated fat in grams per 100 g") var fatSaturatedPer100g: Double?
    @Guide(description: "Carbohydrate in grams per 100 g") var carbohydratePer100g: Double?
    @Guide(description: "Sugars in grams per 100 g") var carbohydrateSugarPer100g: Double?
    @Guide(description: "Fibre in grams per 100 g") var fiberPer100g: Double?
    @Guide(description: "Protein in grams per 100 g") var proteinPer100g: Double?
    @Guide(description: "Salt in grams per 100 g") var saltPer100g: Double?

    init(
        name: String? = nil,
        packageWeightGrams: Double? = nil,
        portions: [NutritionLabelPortionCandidate]? = nil,
        energyKJPer100g: Double? = nil,
        caloriesPer100g: Double? = nil,
        fatPer100g: Double? = nil,
        fatSaturatedPer100g: Double? = nil,
        carbohydratePer100g: Double? = nil,
        carbohydrateSugarPer100g: Double? = nil,
        fiberPer100g: Double? = nil,
        proteinPer100g: Double? = nil,
        saltPer100g: Double? = nil
    ) {
        self.name = name
        self.packageWeightGrams = packageWeightGrams
        self.portions = portions
        self.energyKJPer100g = energyKJPer100g
        self.caloriesPer100g = caloriesPer100g
        self.fatPer100g = fatPer100g
        self.fatSaturatedPer100g = fatSaturatedPer100g
        self.carbohydratePer100g = carbohydratePer100g
        self.carbohydrateSugarPer100g = carbohydrateSugarPer100g
        self.fiberPer100g = fiberPer100g
        self.proteinPer100g = proteinPer100g
        self.saltPer100g = saltPer100g
    }
}

protocol NutritionLabelModelExtractorProtocol {
    func callAsFunction(ocrText: String) async -> NutritionLabelModelCandidate?
}

struct FoundationModelExtractor: NutritionLabelModelExtractorProtocol {

    // MARK: - Functions

    func callAsFunction(ocrText: String) async -> NutritionLabelModelCandidate? {
        guard case .available = SystemLanguageModel.default.availability else { return nil }
        guard !ocrText.isEmpty else { return nil }
        let session = LanguageModelSession(
            instructions: """
            You extract nutrition facts from OCR text read off food packaging. Only report a value \
            that literally appears in the given text. Leave a field nil if you are not sure.
            """
        )
        do {
            let response = try await session.respond(to: ocrText, generating: NutritionLabelModelCandidate.self)
            return response.content
        } catch {
            Log.warning(error, category: Constants.LogCategory.nutritionLabelRecognition)
            return nil
        }
    }
}

#if DEBUG
struct NutritionLabelModelExtractorFake: NutritionLabelModelExtractorProtocol {
    var stubbedCandidate: NutritionLabelModelCandidate?

    func callAsFunction(ocrText: String) async -> NutritionLabelModelCandidate? { stubbedCandidate }
}
#endif
