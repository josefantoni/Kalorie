//
//  BilingualNamed.swift
//  Kalorie
//
//  Created by Josef Antoni on 24.07.2026.
//

import Foundation

protocol BilingualNamed {
    var czName: String { get }
    var engName: String { get }
}

extension Locale {
    static var isCzechOrSlovak: Bool {
        let langCode = Locale.current.language.languageCode?.identifier ?? ""
        return ["cs", "sk"].contains(langCode)
    }
}

extension BilingualNamed {
    var displayName: String {
        if Locale.isCzechOrSlovak { return czName.isEmpty ? engName : czName }
        return engName.isEmpty ? czName : engName
    }
}
