//
//  MealTypeSheetRouter.swift
//  Kalorie
//
//  Created by Josef Antoni on 19.09.2026.
//

import Foundation

struct MealTypeSheetRouter {

    // MARK: - Properties

    private let exportConfigurator: ExportConfigurator

    // MARK: - Init

    init(exportConfigurator: ExportConfigurator = ExportConfigurator()) {
        self.exportConfigurator = exportConfigurator
    }

    // MARK: - Functions

    func makeExportView(mealTypes: [MealTypeDomain]) -> ExportView {
        exportConfigurator.createView(mealTypes: mealTypes)
    }
}
