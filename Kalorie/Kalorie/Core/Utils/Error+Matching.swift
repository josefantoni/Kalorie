//
//  Error+Matching.swift
//  Kalorie
//
//  Created by Josef Antoni on 02.09.2026.
//

import Foundation

extension Error {
    func matches(domain: String, code: Int) -> Bool {
        let nsError = self as NSError
        return nsError.domain == domain && nsError.code == code
    }
}
