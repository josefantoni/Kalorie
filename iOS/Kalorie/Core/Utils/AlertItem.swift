//
//  AlertItem.swift
//  Kalorie
//
//  Created by Josef Antoni on 27.07.2026.
//

import Foundation

struct AlertItem: Identifiable {
    let id = UUID()
    let title: String
    let message: String?

    init(title: String, message: String? = nil) {
        self.title = title
        self.message = message
    }
}
