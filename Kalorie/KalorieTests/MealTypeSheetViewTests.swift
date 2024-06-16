//
//  MealTypeSheetViewTests.swift
//  KalorieTests
//
//  Created by Josef Antoni on 08.06.2024.
//

import SnapshotTesting
import XCTest
import SwiftUI
import CoreData
@testable import Kalorie


final class MealTypeSheetViewTests: XCTestCase {
        
    // MARK: Tests
    
    func testBaseView() {
        let container = PersistentContainer.container
        let context = container.viewContext
        let state = MealTypeSheetViewState(
            mealTypes: DemoData.demoMeals(on: context),
            container: container
        )
        let viewModel = MealTypeSheetViewModel(state)
        let vc = MealTypeSheetView(viewModel: viewModel)
        let view = UIHostingController(rootView: vc)
        assertSnapshot(of: view, as: .image(on: .iPhone13))
    }
    
    func testCreateNewMealTypeView() {
        let container = PersistentContainer.container
        let context = container.viewContext
        let state = MealTypeSheetViewState(
            mealTypes: DemoData.demoMeals(on: context),
            container: container
        )
        let viewModel = MealTypeSheetViewModel(state)
        let vc = MealTypeSheetView(viewModel: viewModel)
        let time = viewModel.getPossibleTimeForNewMealCreation()
        vc.showAddButton(
            possibleStart: time.possibleStart,
            possibleEnd: time.possibleEnd
        )
        let view = UIHostingController(rootView: vc)
        assertSnapshot(of: view, as: .image(on: .iPhone13))
    }
}
