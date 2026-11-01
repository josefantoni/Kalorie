//
//  View+Loader.swift
//  Kalorie
//
//  Created by Josef Antoni on 30.06.2026.
//

import SwiftUI

struct LoaderIndicator: View {

    // MARK: - Body

    var body: some View {
        LoaderSpinner()
            .frame(width: Constants.Loader.size, height: Constants.Loader.size)
            .background(Color(.systemGroupedBackground), in: .rect(cornerRadius: Constants.Loader.cornerRadius))
    }
}

private struct LoaderSpinner: UIViewRepresentable {

    // MARK: - Functions

    func makeUIView(context: Context) -> UIActivityIndicatorView {
        let spinner = UIActivityIndicatorView(style: .medium)
        spinner.color = UIColor(resource: .accent)
        spinner.startAnimating()
        return spinner
    }

    func updateUIView(_ uiView: UIActivityIndicatorView, context: Context) {}
}

extension View {
    func loader(_ isLoading: Bool) -> some View {
        overlay {
            if isLoading {
                LoaderIndicator()
            }
        }
        .allowsHitTesting(!isLoading)
    }
}
