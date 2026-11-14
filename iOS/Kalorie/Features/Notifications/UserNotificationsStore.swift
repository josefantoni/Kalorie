//
//  UserNotificationsStore.swift
//  Kalorie
//
//  Created by Josef Antoni on 14.11.2026.
//

import Foundation

protocol UserNotificationsStoreProtocol: AnyObject {
    var mayHaveUnread: Bool { get set }
    var lastCheckedAt: Date? { get set }
    var lastSubmittedAt: Date? { get set }
}

extension UserNotificationsStoreProtocol {
    func recordFetch(_ notifications: [UserNotificationDomain], startedAt: Date) {
        if let lastSubmittedAt, lastSubmittedAt > startedAt, lastSubmittedAt <= .now { return }
        mayHaveUnread = notifications.contains { $0.isUnread }
        lastCheckedAt = .now
    }
}

final class UserNotificationsStore: UserNotificationsStoreProtocol {

    // MARK: - Properties

    private let userDefaults: UserDefaults
    private let authProvider: any AuthProviderProtocol

    var mayHaveUnread: Bool {
        get {
            guard let key = key("mayHaveUnread") else { return false }
            return userDefaults.bool(forKey: key)
        }
        set {
            guard let key = key("mayHaveUnread") else { return }
            userDefaults.set(newValue, forKey: key)
        }
    }

    var lastCheckedAt: Date? {
        get {
            guard let key = key("lastCheckedAt") else { return nil }
            return userDefaults.object(forKey: key) as? Date
        }
        set {
            guard let key = key("lastCheckedAt") else { return }
            userDefaults.set(newValue, forKey: key)
        }
    }

    var lastSubmittedAt: Date? {
        get {
            guard let key = key("lastSubmittedAt") else { return nil }
            return userDefaults.object(forKey: key) as? Date
        }
        set {
            guard let key = key("lastSubmittedAt") else { return }
            userDefaults.set(newValue, forKey: key)
        }
    }

    // MARK: - Init

    init(authProvider: any AuthProviderProtocol, userDefaults: UserDefaults = .standard) {
        self.authProvider = authProvider
        self.userDefaults = userDefaults
    }

    // MARK: - Private

    private func key(_ name: String) -> String? {
        authProvider.userId.map { "userNotifications.\($0).\(name)" }
    }
}

#if DEBUG
final class UserNotificationsStoreFake: UserNotificationsStoreProtocol {

    // MARK: - Properties

    var mayHaveUnread: Bool
    var lastCheckedAt: Date?
    var lastSubmittedAt: Date?

    // MARK: - Init

    init(mayHaveUnread: Bool = false, lastCheckedAt: Date? = nil, lastSubmittedAt: Date? = nil) {
        self.mayHaveUnread = mayHaveUnread
        self.lastCheckedAt = lastCheckedAt
        self.lastSubmittedAt = lastSubmittedAt
    }
}
#endif
