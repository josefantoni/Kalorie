//
//  SignInSpotlightStore.swift
//  Kalorie
//
//  Created by Josef Antoni on 28.09.2026.
//

import Foundation

protocol SignInSpotlightStoreProtocol: AnyObject {
    var lastShownAt: Date? { get set }
}

final class SignInSpotlightStore: SignInSpotlightStoreProtocol {

    // MARK: - Properties

    private let userDefaults: UserDefaults
    private let key = "signInSpotlightLastShownAt"

    var lastShownAt: Date? {
        get { userDefaults.object(forKey: key) as? Date }
        set { userDefaults.set(newValue, forKey: key) }
    }

    // MARK: - Init

    init(userDefaults: UserDefaults = .standard) {
        self.userDefaults = userDefaults
    }
}

#if DEBUG
final class SignInSpotlightStoreFake: SignInSpotlightStoreProtocol {

    // MARK: - Properties

    var lastShownAt: Date?

    // MARK: - Init

    init(lastShownAt: Date? = nil) {
        self.lastShownAt = lastShownAt
    }
}
#endif
