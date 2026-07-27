//
//  String+Extension.swift
//  Kalorie
//
//  Created by Josef Antoni on 27.07.2026.
//

import Foundation

extension String {
    func decodingHTMLEntities() -> String {
        var result = self
        let entities: [(String, String)] = [
            ("&amp;", "&"), ("&quot;", "\""), ("&lt;", "<"),
            ("&gt;", ">"), ("&apos;", "'"), ("&#39;", "'"), ("&nbsp;", " ")
        ]
        for (entity, replacement) in entities {
            result = result.replacingOccurrences(of: entity, with: replacement)
        }
        return result
    }
}
