//
//  ModerationReviewViewModel.swift
//  Kalorie
//
//  Created by Josef Antoni on 10.09.2026.
//

import Foundation
import UIKit

final class ModerationReviewViewModel: ObservableObject, NutritionLabelPrefilling {

    // MARK: - Properties

    @Published var formInput: FoodItemFormInput
    @Published private(set) var state: LoadingState<Void> = .idle
    @Published var alertItem: AlertItem?
    @Published var isRejectSheetVisible = false
    @Published var rejectReason = ""
    @Published private(set) var shouldDismiss = false
    @Published var recognizedFields: Set<FoodItemFormField> = []
    @Published var isRecognizingNutritionLabel = false
    @Published var isNutritionLabelCameraVisible = false
    @Published var nutritionLabelCameraHint: String?
    @Published private(set) var similarCatalogueItems: [FoodItemDomain] = []
    @Published private(set) var isSimilarCatalogueItemsSectionAvailable = true

    let submittedAt: Date
    let rejectReasonIfAny: String?

    var showsSimilarCatalogueItemsSection: Bool { submission.barcode == nil }

    private let submission: FoodItemSubmissionDomain
    private let approveSubmission: any ApproveSubmissionUseCaseProtocol
    private let rejectSubmission: any RejectSubmissionUseCaseProtocol
    private let searchFoodItems: any SearchFoodItemsUseCaseProtocol
    private let recognizeNutritionLabelUseCase: any RecognizeNutritionLabelUseCaseProtocol
    private let cameraAuthorizationProvider: any CameraAuthorizationProviderProtocol
    private let onResolved: () -> Void

    // MARK: - Init

    init(
        submission: FoodItemSubmissionDomain,
        approveSubmission: any ApproveSubmissionUseCaseProtocol,
        rejectSubmission: any RejectSubmissionUseCaseProtocol,
        searchFoodItems: any SearchFoodItemsUseCaseProtocol,
        recognizeNutritionLabel: any RecognizeNutritionLabelUseCaseProtocol,
        cameraAuthorizationProvider: any CameraAuthorizationProviderProtocol,
        onResolved: @escaping () -> Void
    ) {
        self.submission = submission
        self.formInput = FoodItemFormInput(item: submission.item)
        self.submittedAt = submission.submittedAt
        self.rejectReasonIfAny = submission.rejectReason
        self.approveSubmission = approveSubmission
        self.rejectSubmission = rejectSubmission
        self.searchFoodItems = searchFoodItems
        self.recognizeNutritionLabelUseCase = recognizeNutritionLabel
        self.cameraAuthorizationProvider = cameraAuthorizationProvider
        self.onResolved = onResolved
    }

    // MARK: - Functions

    @MainActor
    func onAppear() async {
        guard showsSimilarCatalogueItemsSection else { return }
        do {
            similarCatalogueItems = try await searchFoodItems(query: submission.item.czName)
        } catch {
            Log.warning(error, category: Constants.LogCategory.moderation)
            isSimilarCatalogueItemsSectionAvailable = false
        }
    }

    @MainActor
    func onNutritionLabelCaptured(_ image: UIImage, liveBarcode: String?) async {
        await recognizeNutritionLabel(from: image, liveBarcode: liveBarcode, using: recognizeNutritionLabelUseCase)
    }

    @MainActor
    func onNutritionLabelCameraTapped() async {
        await openNutritionLabelCamera(using: cameraAuthorizationProvider) {
            alertItem = AlertItem(title: L10n.AddFood.cameraPermissionAlert)
        }
    }

    @MainActor
    func onApproveTapped() async {
        state = .loading
        defer { state = .loaded }
        let editedItem = formInput.asFoodItemDomain(date: submission.item.date)
        do {
            try await approveSubmission(submission: submission, item: editedItem)
            onResolved()
            shouldDismiss = true
        } catch ApproveSubmissionError.alreadyResolved {
            onResolved()
            alertItem = AlertItem(title: L10n.Moderation.errorAlreadyResolved)
            shouldDismiss = true
        } catch ApproveSubmissionError.changedSinceReview {
            onResolved()
            alertItem = AlertItem(title: L10n.Moderation.errorChangedSinceReview)
            shouldDismiss = true
        } catch {
            Log.error(error, category: Constants.LogCategory.moderation)
            switch error as? CreateFoodItemError {
            case .invalidCode:
                alertItem = AlertItem(title: L10n.AddFood.errorInvalidCode)
            case .invalidName:
                alertItem = AlertItem(title: L10n.AddFood.errorInvalidName)
            case .invalidCalories:
                alertItem = AlertItem(title: L10n.AddFood.errorInvalidCalories)
            case .invalidWeight:
                alertItem = AlertItem(title: L10n.AddFood.errorInvalidWeight)
            case .invalidPortion(let portionError):
                alertItem = AlertItem(title: portionError.alertTitle)
            case .itemAlreadyExists:
                alertItem = AlertItem(title: L10n.Moderation.errorAlreadyExists)
            case nil:
                alertItem = AlertItem(title: L10n.Common.errorUnknown)
            }
        }
    }

    @MainActor
    func onRejectConfirmed() async {
        state = .loading
        defer { state = .loaded }
        do {
            try await rejectSubmission(submission, reason: rejectReason)
            onResolved()
            shouldDismiss = true
        } catch RejectSubmissionError.reasonRequired {
            alertItem = AlertItem(title: L10n.Moderation.errorReasonRequired)
        } catch RejectSubmissionError.alreadyResolved {
            onResolved()
            alertItem = AlertItem(title: L10n.Moderation.errorAlreadyResolved)
            shouldDismiss = true
        } catch RejectSubmissionError.changedSinceReview {
            onResolved()
            alertItem = AlertItem(title: L10n.Moderation.errorChangedSinceReview)
            shouldDismiss = true
        } catch {
            Log.error(error, category: Constants.LogCategory.moderation)
            alertItem = AlertItem(title: L10n.Common.errorUnknown)
        }
    }
}
