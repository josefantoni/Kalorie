//
//  FixtureLoader.swift
//  KalorieTests
//
//  Created by Josef Antoni on 24.09.2026.
//

import Foundation

enum FixtureLoader {

    // MARK: - Functions

    static func load<T: Decodable>(_ name: String, filePath: StaticString = #filePath) throws -> T {
        let repositoryRoot = URL(fileURLWithPath: "\(filePath)")
            .deletingLastPathComponent()
            .deletingLastPathComponent()
            .deletingLastPathComponent()
        let url = repositoryRoot.appendingPathComponent("fixtures/\(name).json")
        return try JSONDecoder().decode(T.self, from: Data(contentsOf: url))
    }
}
