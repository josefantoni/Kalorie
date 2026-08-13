//
//  String+Extension.swift
//  Kalorie
//
//  Created by Josef Antoni on 27.07.2026.
//

import Foundation
import TextKit

extension String {
    func decodingHTMLEntities() -> String {
        HtmlEntitiesKt.decodeHtmlEntities(input: self)
    }
}
