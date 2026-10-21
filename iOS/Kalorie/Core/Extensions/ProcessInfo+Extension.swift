//
//  ProcessInfo+Extension.swift
//  Kalorie
//
//  Created by Josef Antoni on 21.10.2026.
//

import Foundation

extension ProcessInfo {

    static var isRunningOnMac: Bool {
        processInfo.isiOSAppOnMac
    }
}
