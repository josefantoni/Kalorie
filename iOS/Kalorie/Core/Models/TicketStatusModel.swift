//
//  TicketStatusModel.swift
//  Kalorie
//
//  Created by Josef Antoni on 14.11.2026.
//

import Foundation

enum TicketStatus: String, Codable, Equatable {
    case pending
    case accepted
    case rejected
}

enum TicketLocale {

    // MARK: - Functions

    static func current(preferredLocalizations: [String] = Bundle.main.preferredLocalizations) -> String {
        let language = preferredLocalizations.first.map { String($0.prefix(2)) }
        guard let language, Constants.Firestore.supportedLocales.contains(language) else {
            return Constants.Firestore.fallbackLocale
        }
        return language
    }

    static func resolved(_ locale: String?) -> String {
        guard let locale, Constants.Firestore.supportedLocales.contains(locale) else {
            return Constants.Firestore.fallbackLocale
        }
        return locale
    }
}
