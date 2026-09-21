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

extension BilingualNamed {
    var displayName: String {
        let langCode = Locale.current.language.languageCode?.identifier ?? ""
        let isCzechOrSlovak = ["cs", "sk"].contains(langCode)
        if isCzechOrSlovak { return czName }
        return engName.isEmpty ? czName : engName
    }
}
