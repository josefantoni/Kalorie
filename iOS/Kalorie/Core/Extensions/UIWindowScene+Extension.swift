//
//  UIWindowScene+Extension.swift
//  Kalorie
//
//  Created by Josef Antoni on 10.08.2026.
//

import UIKit

extension UIWindowScene {
    var keyWindow: UIWindow? {
        windows.first(where: \.isKeyWindow)
    }
}
