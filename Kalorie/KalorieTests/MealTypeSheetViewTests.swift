//
//  MealTypeSheetViewTests.swift
//  KalorieTests
//
//  Created by Josef Antoni on 08.06.2024.
//

import SnapshotTesting
import XCTest
import SwiftUI
@testable import Kalorie

final class MealTypeSheetViewTests: XCTestCase {
        
    func testMyViewController() {
        let vc = MealTypeSheetView()
        let view = UIHostingController(rootView: vc)
        assertSnapshot(of: view, as: .image(on: .iPhone13))
    }
}
