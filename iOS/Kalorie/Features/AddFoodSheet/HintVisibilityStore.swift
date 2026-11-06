//
//  HintVisibilityStore.swift
//  Kalorie
//
//  Created by Josef Antoni on 30.10.2026.
//

import Foundation

protocol HintVisibilityStoreProtocol: AnyObject {
    var hiddenAt: Date? { get set }
    var hideCount: Int { get set }
}

extension HintVisibilityStoreProtocol {
    private static var hideDays: [Int] { [3, 5, 8, 13, 21, 30] }

    func isHintVisible(now: Date) -> Bool {
        guard let hiddenAt else { return true }
        let steps = Self.hideDays
        let days = steps[min(max(hideCount, 1), steps.count) - 1]
        return now.timeIntervalSince(hiddenAt) >= TimeInterval(days) * 24 * 60 * 60
    }
}

final class HintVisibilityStore: HintVisibilityStoreProtocol {

    // MARK: - Properties

    private let userDefaults: UserDefaults
    private let hiddenAtKey: String
    private let hideCountKey: String

    var hiddenAt: Date? {
        get { userDefaults.object(forKey: hiddenAtKey) as? Date }
        set { userDefaults.set(newValue, forKey: hiddenAtKey) }
    }

    var hideCount: Int {
        get { userDefaults.integer(forKey: hideCountKey) }
        set { userDefaults.set(newValue, forKey: hideCountKey) }
    }

    // MARK: - Init

    init(
        hiddenAtKey: String,
        hideCountKey: String,
        userDefaults: UserDefaults = .standard
    ) {
        self.hiddenAtKey = hiddenAtKey
        self.hideCountKey = hideCountKey
        self.userDefaults = userDefaults
    }
}

#if DEBUG
final class HintVisibilityStoreFake: HintVisibilityStoreProtocol {

    // MARK: - Properties

    var hiddenAt: Date?
    var hideCount: Int

    // MARK: - Init

    init(hiddenAt: Date? = nil, hideCount: Int = 0) {
        self.hiddenAt = hiddenAt
        self.hideCount = hideCount
    }
}
#endif
