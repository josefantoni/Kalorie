//
//  FoodItemReporting.swift
//  Kalorie
//
//  Created by Josef Antoni on 17.09.2026.
//

import Foundation

protocol FoodItemReporting: AnyObject {
    var hasReportedCurrentItem: Bool { get set }
    var isSubmittingReport: Bool { get set }
    var isReportReasonAlertVisible: Bool { get set }
    var reportReasonText: String { get set }
    var alertItem: AlertItem? { get set }
}

extension FoodItemReporting {

    @MainActor
    func loadReportState(barcode: String, fetchMyFoodItemReport: any FetchMyFoodItemReportUseCaseProtocol) async {
        do {
            hasReportedCurrentItem = try await fetchMyFoodItemReport(barcode: barcode) != nil
        } catch {
            Log.warning(error, category: Constants.LogCategory.foodItemReport)
            hasReportedCurrentItem = false
        }
    }

    @MainActor
    func onReportIncorrectDataTapped() {
        guard !hasReportedCurrentItem else { return }
        reportReasonText = ""
        isReportReasonAlertVisible = true
    }

    @MainActor
    func onReportSubmitted(barcode: String, submitFoodItemReport: any SubmitFoodItemReportUseCaseProtocol) async {
        guard !isSubmittingReport else { return }
        isSubmittingReport = true
        defer { isSubmittingReport = false }
        do {
            try await submitFoodItemReport(barcode: barcode, reason: reportReasonText)
            hasReportedCurrentItem = true
        } catch FoodItemReportError.reasonRequired {
            alertItem = AlertItem(title: L10n.FoodItemReport.errorReasonRequired)
        } catch FoodItemReportError.reasonTooLong {
            alertItem = AlertItem(title: L10n.FoodItemReport.errorReasonTooLong)
        } catch {
            Log.error(error, category: Constants.LogCategory.foodItemReport)
            alertItem = AlertItem(title: L10n.Common.errorUnknown)
        }
    }
}
