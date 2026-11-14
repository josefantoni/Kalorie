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
    @Published var responseDraft: ModerationResponseDraft?
    @Published private(set) var isSendingResponse = false
    @Published private(set) var shouldDismiss = false

    private let fetchFoodItemByBarcode: any FetchFoodItemByBarcodeUseCaseProtocol
    private let updateFoodItem: any UpdateFoodItemUseCaseProtocol
    private let resolveFoodItemReport: any ResolveFoodItemReportUseCaseProtocol
    private let modelExtractor: any NutritionLabelModelExtractorProtocol
    private let cameraAuthorizationProvider: any CameraAuthorizationProviderProtocol
    private let onResolved: () -> Void

    let ticket: FoodItemReportDomain?

    var isOpenedFromReport: Bool { ticket != nil }

    // MARK: - Init

    init(
        fetchFoodItemByBarcode: any FetchFoodItemByBarcodeUseCaseProtocol,
        updateFoodItem: any UpdateFoodItemUseCaseProtocol,
        resolveFoodItemReport: any ResolveFoodItemReportUseCaseProtocol,
        modelExtractor: any NutritionLabelModelExtractorProtocol,
        cameraAuthorizationProvider: any CameraAuthorizationProviderProtocol,
        ticket: FoodItemReportDomain? = nil,
        onResolved: @escaping () -> Void = {}
    ) {
        self.fetchFoodItemByBarcode = fetchFoodItemByBarcode
        self.updateFoodItem = updateFoodItem
        self.resolveFoodItemReport = resolveFoodItemReport
        self.modelExtractor = modelExtractor
        self.cameraAuthorizationProvider = cameraAuthorizationProvider
        self.ticket = ticket
        self.onResolved = onResolved
        self.barcodeQuery = ticket?.barcode ?? ""
    }

    // MARK: - Functions

    @MainActor
    func onAppear() async {
        guard let ticket, loadedItem == nil else { return }
        barcodeQuery = ticket.barcode
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
        guard !state.isLoading else { return }
        guard await save() else { return }
        showCheckmark = true
        try? await Task.sleep(for: .seconds(2))
        showCheckmark = false
    }

    @MainActor
    func onSaveAndResolveTapped() async {
        guard let ticket, !state.isLoading else { return }
        guard await save() else { return }
        responseDraft = ModerationResponseDraft(
            status: .accepted,
            text: L10n.Moderation.responseReportAccepted(locale: ticket.locale)
        )
    }

    @MainActor
    func onRejectTapped() {
        guard ticket != nil, !state.isLoading else { return }
        responseDraft = ModerationResponseDraft(status: .rejected, text: "")
    }

    @MainActor
    func onResponseSendTapped() async {
        guard
            let ticket,
            let draft = responseDraft,
            draft.isSendable,
            !isSendingResponse
        else { return }
        isSendingResponse = true
        defer { isSendingResponse = false }
        do {
            try await resolveFoodItemReport(ticketId: ticket.id, status: draft.status, response: draft.text)
        } catch {
            Log.error(error, category: Constants.LogCategory.moderation)
            responseDraft?.errorTitle = L10n.Common.errorUnknown
            return
        }
        responseDraft = nil
        onResolved()
        shouldDismiss = true
    }

    // MARK: - Private

    @MainActor
    private func save() async -> Bool {
        guard let loadedItem else { return false }
        state = .loading
        defer { state = .loaded }
        let item = formInput.asFoodItemDomain(id: loadedItem.id, date: loadedItem.date)
        do {
            let savedItem = try await updateFoodItem(item, previouslyLoaded: loadedItem, photo: formInput.photo)
            self.loadedItem = savedItem
            formInput.photo = savedItem.photoURL.map(FoodItemFormPhoto.remote) ?? .none
            return true
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
            return false
        }
    }
}
