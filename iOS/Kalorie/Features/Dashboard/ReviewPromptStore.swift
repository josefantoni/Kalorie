//
//  ReviewPromptStore.swift
//  Kalorie
//
//  Created by Josef Antoni on 22.11.2026.
//

import Foundation

protocol ReviewPromptStoreProtocol: AnyObject {
    var loggingDays: Set<String> { get set }
    var dismissCount: Int { get set }
    var lastDismissedAt: Date? { get set }
    var isCompleted: Bool { get set }
}

final class ReviewPromptStore: ReviewPromptStoreProtocol {

    // MARK: - Properties

    private let userDefaults: UserDefaults
    private let loggingDaysKey = "reviewPromptLoggingDays"
    private let dismissCountKey = "reviewPromptDismissCount"
    private let lastDismissedAtKey = "reviewPromptLastDismissedAt"
    private let isCompletedKey = "reviewPromptIsCompleted"

    var loggingDays: Set<String> {
        get { Set(userDefaults.stringArray(forKey: loggingDaysKey) ?? []) }
        set { userDefaults.set(Array(newValue), forKey: loggingDaysKey) }
    }

    var dismissCount: Int {
        get { userDefaults.integer(forKey: dismissCountKey) }
        set { userDefaults.set(newValue, forKey: dismissCountKey) }
    }

    var lastDismissedAt: Date? {
        get { userDefaults.object(forKey: lastDismissedAtKey) as? Date }
        set { userDefaults.set(newValue, forKey: lastDismissedAtKey) }
    }

    var isCompleted: Bool {
        get { userDefaults.bool(forKey: isCompletedKey) }
        set { userDefaults.set(newValue, forKey: isCompletedKey) }
    }

    // MARK: - Init

    init(userDefaults: UserDefaults = .standard) {
        self.userDefaults = userDefaults
    }
}

#if DEBUG
final class ReviewPromptStoreFake: ReviewPromptStoreProtocol {

    // MARK: - Properties

    var loggingDays: Set<String>
    var dismissCount: Int
    var lastDismissedAt: Date?
    var isCompleted: Bool

    // MARK: - Init

    init(
        loggingDays: Set<String> = [],
        dismissCount: Int = 0,
        lastDismissedAt: Date? = nil,
        isCompleted: Bool = false
    ) {
        self.loggingDays = loggingDays
        self.dismissCount = dismissCount
        self.lastDismissedAt = lastDismissedAt
        self.isCompleted = isCompleted
    }
}
#endif
