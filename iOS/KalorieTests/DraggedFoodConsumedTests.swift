//
//  DraggedFoodConsumedTests.swift
//  KalorieTests
//
//  Created by Josef Antoni on 13.10.2026.
//

import UniformTypeIdentifiers
import XCTest
@testable import Kalorie

final class DraggedFoodConsumedTests: XCTestCase {

    func test_loadId_roundTripsTheIdOfTheDraggedEntry() async {
        let provider = DraggedFoodConsumed(id: "entry-1").itemProvider

        let id = await DraggedFoodConsumed.loadId(from: [provider])

        XCTAssertEqual(id, "entry-1")
    }

    func test_loadId_withNoProviders_returnsNil() async {
        let id = await DraggedFoodConsumed.loadId(from: [])

        XCTAssertNil(id)
    }

    func test_loadId_withForeignText_returnsNil() async {
        let provider = NSItemProvider(object: "entry-1" as NSString)

        let id = await DraggedFoodConsumed.loadId(from: [provider])

        XCTAssertNil(id, "only the app's own payload may move an entry, so text dragged in from elsewhere is ignored")
    }
}
