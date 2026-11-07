//
//  AccentProgressView.swift
//  Kalorie
//
//  Created by Josef Antoni on 07.11.2026.
//

import SwiftUI

struct AccentProgressView: UIViewRepresentable {

    // MARK: - Functions

    func makeUIView(context: Context) -> UIActivityIndicatorView {
        let spinner = UIActivityIndicatorView(style: .medium)
        spinner.color = UIColor(resource: .accent)
        spinner.startAnimating()
        return spinner
    }

    func updateUIView(_ uiView: UIActivityIndicatorView, context: Context) {}
}

#if DEBUG
// MARK: - Preview

#Preview {
    AccentProgressView()
}
#endif
