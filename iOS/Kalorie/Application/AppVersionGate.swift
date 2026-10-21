//
//  AppVersionGate.swift
//  Kalorie
//
//  Created by Josef Antoni on 21.10.2026.
//

import Foundation

@MainActor
final class AppVersionGate: ObservableObject {

    // MARK: - Properties

    @Published private(set) var isUpdateRequired = false

    private let fetchMinimumVersion: any FetchMinimumVersionUseCaseProtocol
    private let currentVersion: AppVersion?

    // MARK: - Init

    init(fetchMinimumVersion: any FetchMinimumVersionUseCaseProtocol, currentVersion: AppVersion?) {
        self.fetchMinimumVersion = fetchMinimumVersion
        self.currentVersion = currentVersion
    }

    convenience init(fetchMinimumVersion: any FetchMinimumVersionUseCaseProtocol) {
        let version = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String
        self.init(fetchMinimumVersion: fetchMinimumVersion, currentVersion: version.flatMap(AppVersion.init))
    }

    // MARK: - Functions

    func check() async {
        guard let currentVersion else { return }
        do {
            let minimum = try await fetchMinimumVersion()
            isUpdateRequired = minimum.map { currentVersion < $0 } ?? false
        } catch {
            Log.error(error, category: Constants.LogCategory.appVersion)
        }
    }
}
