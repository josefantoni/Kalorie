//
//  AppVersion.swift
//  Kalorie
//
//  Created by Josef Antoni on 21.10.2026.
//

import Foundation

struct AppVersion: Comparable {

    // MARK: - Properties

    private let components: [Int]

    // MARK: - Init

    init?(_ string: String) {
        let parts = string.split(separator: ".", omittingEmptySubsequences: false)
        let parsed = parts.compactMap { part in
            part.allSatisfy { $0.isASCII && $0.isNumber } ? Int(part) : nil
        }
        guard
            !parts.isEmpty,
            parsed.count == parts.count
        else { return nil }
        components = parsed
    }

    // MARK: - Functions

    static func == (lhs: AppVersion, rhs: AppVersion) -> Bool {
        compare(lhs, rhs) == 0
    }

    static func < (lhs: AppVersion, rhs: AppVersion) -> Bool {
        compare(lhs, rhs) < 0
    }

    private static func compare(_ lhs: AppVersion, _ rhs: AppVersion) -> Int {
        for index in 0..<max(lhs.components.count, rhs.components.count) {
            let left = index < lhs.components.count ? lhs.components[index] : 0
            let right = index < rhs.components.count ? rhs.components[index] : 0
            if left != right { return left < right ? -1 : 1 }
        }
        return 0
    }
}
