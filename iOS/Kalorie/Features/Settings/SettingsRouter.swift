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
    private let supportConfigurator: SupportConfigurator

    // MARK: - Init

    init(
        moderationConfigurator: ModerationConfigurator,
        exportConfigurator: ExportConfigurator = ExportConfigurator(),
        supportConfigurator: SupportConfigurator = SupportConfigurator()
    ) {
        self.exportConfigurator = exportConfigurator
        self.moderationConfigurator = moderationConfigurator
        self.supportConfigurator = supportConfigurator
    }

    // MARK: - Functions

    func makeExportView(mealTypes: [MealTypeDomain]) -> ExportView {
        exportConfigurator.createView(mealTypes: mealTypes)
    }

    func makeSupportView() -> SupportView {
        supportConfigurator.createView()
    }

    func makeModerationQueueView() -> ModerationQueueView {
        moderationConfigurator.createView()
    }

    func makeModerationReportsView() -> ModerationReportsView {
        moderationConfigurator.createReportsView()
    }
}
