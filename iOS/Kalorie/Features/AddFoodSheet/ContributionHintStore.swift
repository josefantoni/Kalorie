//
//  ContributionHintStore.swift
//  Kalorie
//
//  Created by Josef Antoni on 30.10.2026.
//

import Foundation

protocol ContributionHintStoreProtocol: AnyObject {
    var hiddenAt: Date? { get set }
    var hideCount: Int { get set }
}

final class ContributionHintStore: ContributionHintStoreProtocol {

    // MARK: - Properties

    private let userDefaults: UserDefaults
    private let hiddenAtKey = Constants.AddFood.contributionHintHiddenAtKey
    private let hideCountKey = Constants.AddFood.contributionHintHideCountKey

    var hiddenAt: Date? {
        get { userDefaults.object(forKey: hiddenAtKey) as? Date }
        set { userDefaults.set(newValue, forKey: hiddenAtKey) }
    }

    var hideCount: Int {
        get { userDefaults.integer(forKey: hideCountKey) }
        set { userDefaults.set(newValue, forKey: hideCountKey) }
    }

    // MARK: - Init

    init(userDefaults: UserDefaults = .standard) {
        self.userDefaults = userDefaults
    }
}

#if DEBUG
final class ContributionHintStoreFake: ContributionHintStoreProtocol {

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
