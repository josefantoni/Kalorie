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

    func foldingDiacritics() -> String {
        DiacriticFoldingKt.foldDiacritics(input: self)
    }

    func searchTerms() -> [String] {
        SearchTermsKt.searchTerms(input: self)
    }

    func sanitizedDecimalInput() -> String {
        DecimalInputKt.sanitizeDecimalInput(input: self)
    }

    var decimalInputValue: Double? {
        DecimalInputKt.parseDecimalInput(input: self)?.doubleValue
    }
}
