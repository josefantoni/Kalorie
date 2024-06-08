//
//  KalorieTests.swift
//  KalorieTests
//
//  Created by Josef Antoni on 05.06.2024.
//

import SnapshotTesting
import XCTest
import SwiftUI
@testable import Kalorie

final class DashboardTests: XCTestCase {
    
    func testMyViewController() {
        let vc = DashboardView()
        let view = UIHostingController(rootView: vc)
        assertSnapshot(of: view, as: .image(on: .iPhone13))
    }
}
