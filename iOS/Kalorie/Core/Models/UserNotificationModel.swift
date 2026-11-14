//
//  UserNotificationModel.swift
//  Kalorie
//
//  Created by Josef Antoni on 14.11.2026.
//

import Foundation

struct UserNotificationDomain: Identifiable, Equatable {

    // MARK: - Types

    enum Kind: Equatable {
        case report
        case photoSuggestion

        var collection: String {
            switch self {
            case .report: Constants.Firestore.foodItemReports
            case .photoSuggestion: Constants.Firestore.photoSuggestions
            }
        }
        
        var iconName: String {
            switch self {
            case .report: BaseImageName.pencil.rawValue
            case .photoSuggestion: BaseImageName.cameraFill.rawValue
            }
        }

        var label: String {
            switch self {
            case .report: L10n.Notifications.userKindReport
            case .photoSuggestion: L10n.Notifications.userKindPhoto
            }
        }

        var message: String {
            switch self {
            case .report: L10n.Notifications.userReportMessage
            case .photoSuggestion: L10n.Notifications.userPhotoMessage
            }
        }
    }

    // MARK: - Properties

    let kind: Kind
    let id: String
    let itemId: String
    let createdAt: Date
    var status: TicketStatus = .pending
    var response: String?
    var resolvedAt: Date?
    var seenAt: Date?
    var reason: String?
    var photoURL: URL?

    var rowId: String { "\(kind.collection)/\(id)" }

    var lastEventAt: Date { resolvedAt ?? createdAt }

    var isUnread: Bool {
        guard let seenAt else { return true }
        return seenAt < lastEventAt
    }

    var statusLabel: String {
        switch (status, kind) {
        case (.pending, _): L10n.Notifications.userStatusPending
        case (.accepted, .report): L10n.Notifications.userStatusResolved
        case (.accepted, .photoSuggestion): L10n.Notifications.userStatusApproved
        case (.rejected, _): L10n.Notifications.userStatusRejected
        }
    }
}
