//
//  Log.swift
//  Kalorie
//
//  Created by Josef Antoni on 30.08.2026.
//

import FirebaseCrashlytics
import OSLog

enum Log {

    // MARK: - Functions

    static func warning(_ error: Error, category: String = "app") {
        logger(category).warning("\(String(describing: error), privacy: .public)")
    }

    static func error(_ error: Error, category: String = "app") {
        logger(category).error("\(String(describing: error), privacy: .public)")
        Crashlytics.crashlytics().record(error: error)
    }

    static func logger(_ category: String) -> Logger {
        Logger(subsystem: "antoni.Kalorie", category: category)
    }
}
