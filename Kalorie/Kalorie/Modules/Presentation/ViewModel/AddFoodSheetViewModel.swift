//
//  AddFoodSheetViewModel.swift
//  Kalorie
//
//  Created by Josef Antoni on 18.06.2024.
//
//  swiftlint:disable function_parameter_count

import Foundation
import FirebaseFirestore


enum FoodOnServerErrorType: Error {
    
    case failedToFetchOfFoodsConsumed
    case failedToCreateFoodConsumed
    case failedToMapFoodConsumed
    case invalidCode
    case invalidCalories
    case invalidName
    case invalidWeight
}

struct AddFoodSheetViewState {
    
    // tohle asi bude jinak
    var foodsOnServer: [FoodItemDTO] = []
    
    // UI
    var isAlertVisible = false
    var isAddNewItemVisible: Bool
    var isScannerVisible: Bool
    var alertTitle = ""

    // Form values
    var scannedCode: String?
    var name: String?
    var weightOfProduct: Double?
    var caloriesPerHundredGrams: Double?
    var fat: Double?
    var fatUnsaturatedFattyAcids: Double?
    var carbohydrate: Double?
    var carbohydratePureSugar: Double?
    var protein: Double?
    var salt: Double?
}


class AddFoodSheetViewModel: ObservableObject {
    
    // MARK: - Properties

    @Published var state: AddFoodSheetViewState


    // MARK: - Init
    
    init(state: AddFoodSheetViewState) {
        self.state = state
    }

    
    // MARK: - Functions
    
    func createNewFoodRecord(
        id: String,
        name: String,
        weightOfProduct: Double,
        caloriesPerHundredGrams: Double,
        fat: Double,
        fatUnsaturatedFattyAcids: Double,
        carbohydrate: Double,
        carbohydratePureSugar: Double,
        protein: Double,
        salt: Double
    ) throws {
        if id.isEmpty {
            throw FoodOnServerErrorType.invalidCode
        }
        if name.isEmpty {
            throw FoodOnServerErrorType.invalidName
        }
        if caloriesPerHundredGrams <= 0 {
            throw FoodOnServerErrorType.invalidCalories
        }
        
        if weightOfProduct <= 0 {
            throw FoodOnServerErrorType.invalidWeight
        }
        Task {
            do {
                let foodConsumed = FoodItemDTO(
                    id: id,
                    name: name,
                    weight: weightOfProduct,
                    date: Date.now.timeIntervalSince1970,
                    caloriesPerHundredGrams: caloriesPerHundredGrams,
                    fat: fat,
                    fatUnsaturatedFattyAcids: fatUnsaturatedFattyAcids,
                    carbohydrate: carbohydrate,
                    carbohydratePureSugar: carbohydratePureSugar,
                    protein: protein,
                    salt: salt
                )

                try await Firestore
                    .firestore()
                    .collection("food_items")
                    .addDocument(data: foodConsumed.dictionary)
                
                state.foodsOnServer.append(foodConsumed)
            } catch {
                print("Error adding document: \(error)")
                throw FoodOnServerErrorType.failedToCreateFoodConsumed
            }
        }
    }
    
    func getFoodFromServer() throws {
        Task {
            do {
                let snapshot = try await Firestore
                    .firestore()
                    .collection("food_items")
                    .getDocuments()
               
                do {
                    try await MainActor.run {
                        state.foodsOnServer = try snapshot.documents.map { document in
                             try document.data(as: FoodItemDTO.self)
                        }
                    }
                } catch {
                    throw FoodOnServerErrorType.failedToMapFoodConsumed
                }
            } catch {
                throw FoodOnServerErrorType.failedToFetchOfFoodsConsumed
            }
        }
    }
    
    func isFoodExisting(for code: String) async -> Bool {
        state.foodsOnServer.contains { $0.name.contains(code) }
    }
    
    func refreshAvailableFoodItems() {
        do {
            try getFoodFromServer()
            return
        } catch FoodOnServerErrorType.failedToMapFoodConsumed {
            state.alertTitle = "Nastala chyba při aktualizaci jídel ze serveru, hodnoty jsou poškozené"
        } catch FoodOnServerErrorType.failedToFetchOfFoodsConsumed {
            state.alertTitle = "Nastala chyba při aktualizaci jídel ze serveru"
        } catch {
            state.alertTitle = "Nastala neznámá chyba, při aktualizaci jídel ze serveru"
        }
        state.isAlertVisible.toggle()
    }
    
    func createNewFoodRecordErrorHandler(_ error: Any) {
        guard let err = error as? FoodOnServerErrorType else {
            state.alertTitle = "Nastala neznámá chyba"
            state.isAlertVisible.toggle()
            return
        }
        
        switch err {
        case .invalidCode:
            state.alertTitle = "Neplatný čárový kód"
            
        case .invalidName:
            state.alertTitle = "Překontrolujte zadaný název"
            
        case .invalidCalories:
            state.alertTitle = "Kalorie musí být větší než nula"
            
        case .invalidWeight:
            state.alertTitle = "Hmotnost musí být větší než nula"
            
        default: 
            state.alertTitle = "Vyplňte všechny nezbytné hodnoty"
        }
        state.isAlertVisible.toggle()
    }
}
