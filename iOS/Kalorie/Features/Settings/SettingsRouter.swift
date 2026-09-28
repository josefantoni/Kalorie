//
//  SettingsRouter.swift
//  Kalorie
//
//  Created by Josef Antoni on 19.09.2026.
//

import Foundation

struct SettingsRouter {

    // MARK: - Properties

    private let exportConfigurator: ExportConfigurator
    private let moderationConfigurator: ModerationConfigurator

    // MARK: - Init

    init(
        exportConfigurator: ExportConfigurator = ExportConfigurator(),
        moderationConfigurator: ModerationConfigurator
    ) {
        self.exportConfigurator = exportConfigurator
        self.moderationConfigurator = moderationConfigurator
    }

    // MARK: - Functions

    func makeExportView(mealTypes: [MealTypeDomain]) -> ExportView {
        exportConfigurator.createView(mealTypes: mealTypes)
    }

    func makeModerationQueueView() -> ModerationQueueView {
        moderationConfigurator.createView()
    }

    func makeModerationReportsView() -> ModerationReportsView {
        moderationConfigurator.createReportsView()
    }
}
