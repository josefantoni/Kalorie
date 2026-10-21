//
//  UpdateRequiredView.swift
//  Kalorie
//
//  Created by Josef Antoni on 21.10.2026.
//

import SwiftUI

struct UpdateRequiredView: View {

    // MARK: - Properties

    @Environment(\.openURL) private var openURL

    // MARK: - Body

    var body: some View {
        VStack(spacing: 16) {
            Spacer()
            Text(L10n.UpdateRequired.title)
                .font(.title2)
                .fontWeight(.bold)
            Text(L10n.UpdateRequired.message)
                .font(.body)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
            Spacer()
            if let storeURL = Constants.Store.appStoreURL {
                PrimaryButton(L10n.UpdateRequired.button) {
                    openURL(storeURL)
                }
            }
        }
        .padding(24)
    }
}

#Preview {
    UpdateRequiredView()
}
