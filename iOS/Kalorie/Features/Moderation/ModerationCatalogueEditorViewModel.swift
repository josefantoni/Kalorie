//
//  ModerationCatalogueEditorViewModel.swift
//  Kalorie
//
//  Created by Josef Antoni on 10.09.2026.
//

import Foundation
import UIKit

final class ModerationCatalogueEditorViewModel: ObservableObject, NutritionLabelPrefilling {

    // MARK: - Properties

    @Published var barcodeQuery = ""
    @Published private(set) var loadedItem: FoodItemDomain?
    @Published var formInput = FoodItemFormInput()
    @Published private(set) var state: LoadingState<Void> = .idle
    @Published var alertItem: AlertItem?
    @Published private(set) var showCheckmark = false
    @Published var recognizedFields: Set<FoodItemFormField> = []
    @Published var isRecognizingNutritionLabel = false
    @Published var isNutritionLabelCameraVisible = false
    @Published var nutritionLabelCameraHint: String?

    private let fetchFoodItemByBarcode: any FetchFoodItemByBarcodeUseCaseProtocol
    private let updateFoodItem: any UpdateFoodItemUseCaseProtocol
    private let modelExtractor: any NutritionLabelModelExtractorProtocol
    private let cameraAuthorizationProvider: any CameraAuthorizationProviderProtocol
    private let initialBarcode: String?

    var isOpenedFromReport: Bool { initialBarcode != nil }

    // MARK: - Init

    init(
        fetchFoodItemByBarcode: any FetchFoodItemByBarcodeUseCaseProtocol,
        updateFoodItem: any UpdateFoodItemUseCaseProtocol,
        modelExtractor: any NutritionLabelModelExtractorProtocol,
        cameraAuthorizationProvider: any CameraAuthorizationProviderProtocol,
        initialBarcode: String? = nil
    ) {
        self.fetchFoodItemByBarcode = fetchFoodItemByBarcode
        self.updateFoodItem = updateFoodItem
        self.modelExtractor = modelExtractor
        self.cameraAuthorizationProvider = cameraAuthorizationProvider
        self.initialBarcode = initialBarcode
        self.barcodeQuery = initialBarcode ?? ""
    }

    // MARK: - Functions

    @MainActor
    func onAppear() async {
        guard let initialBarcode, loadedItem == nil else { return }
        barcodeQuery = initialBarcode
        await onSearchTapped()
    }

    @MainActor
    func onNutritionLabelRecognized(_ reading: NutritionLabelReading, ocrText: String, liveBarcode: String?) async {
        await applyRecognizedNutritionLabel(reading, ocrText: ocrText, liveBarcode: liveBarcode, using: modelExtractor)
    }

    @MainActor
    func onNutritionLabelCameraTapped() async {
        await openNutritionLabelCamera(using: cameraAuthorizationProvider) {
            alertItem = AlertItem(title: L10n.AddFood.cameraPermissionAlert)
        }
    }

    @MainActor
    func onSearchTapped() async {
        guard !barcodeQuery.isEmpty else { return }
        state = .loading
        defer { state = .loaded }
        do {
            guard let item = try await fetchFoodItemByBarcode(barcode: barcodeQuery) else {
                loadedItem = nil
                alertItem = AlertItem(title: L10n.AddFood.errorBarcodeNotFound)
                return
            }
            loadedItem = item
            formInput = FoodItemFormInput(item: item)
            recognizedFields = []
            showCheckmark = false
        } catch {
            Log.error(error, category: Constants.LogCategory.moderation)
            alertItem = AlertItem(title: L10n.Common.errorUnknown)
        }
    }

    @MainActor
    func onSaveTapped() async {
        guard let loadedItem else { return }
        state = .loading
        defer { state = .loaded }
        let item = formInput.asFoodItemDomain(id: loadedItem.id, date: loadedItem.date)
        do {
            try await updateFoodItem(item, previouslyLoaded: loadedItem)
            self.loadedItem = item
            state = .loaded
            showCheckmark = true
            try? await Task.sleep(for: .seconds(2))
            showCheckmark = false
        } catch {
            Log.error(error, category: Constants.LogCategory.moderation)
            switch error as? UpdateFoodItemError {
            case .invalidCode:
                alertItem = AlertItem(title: L10n.AddFood.errorInvalidCode)
            case .invalidName:
                alertItem = AlertItem(title: L10n.AddFood.errorInvalidName)
            case .invalidCalories:
                alertItem = AlertItem(title: L10n.AddFood.errorInvalidCalories)
            case .invalidAlcoholByVolume:
                alertItem = AlertItem(title: L10n.AddFood.errorInvalidAlcoholByVolume)
            case .invalidPortion(let portionError):
                alertItem = AlertItem(title: portionError.alertTitle)
            case .changedSinceLoad:
                alertItem = AlertItem(title: L10n.Moderation.errorItemChangedSinceLoad)
            case nil:
                alertItem = AlertItem(title: L10n.Common.errorUnknown)
            }
        }
    }

}
