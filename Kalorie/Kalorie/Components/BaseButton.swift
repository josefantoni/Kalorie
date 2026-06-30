//
//  BaseButton.swift
//  Kalorie
//
//  Created by Josef Antoni on 29.06.2024.
//

import Foundation
import SwiftUI

struct BaseButton: View {
    
    enum BaseButtonStyle {
        case plain
        case capsuled
        case capsuledLong
    }
    
    // MARK: - Properties

    let style: BaseButtonStyle
    let imageName: BaseImageName
    let imageSize: CGFloat
    let imageWeight: Font.Weight
    let action: () -> Void
    
    // MARK: - Init

    init(
        style: BaseButtonStyle,
        imageName: BaseImageName,
        imageSize: CGFloat = .basic,
        imageWeight: Font.Weight = .thin,
        action: @escaping () -> Void
    ) {
        self.style = style
        self.imageName = imageName
        self.imageSize = imageSize
        self.imageWeight = imageWeight
        self.action = action
    }
    
    // MARK: - Body

    var body: some View {
        switch style {
        case .plain:
            Button { action() } label: { image }
                .buttonStyle(.borderless)
        case .capsuled:
            Button { action() } label: { image }
                .clipShape(Capsule())
                .buttonStyle(.borderedProminent)
        case .capsuledLong:
            Button { action() } label: { image.frame(maxWidth: .infinity) }
                .clipShape(Capsule())
                .buttonStyle(.borderedProminent)
        }
    }

    // MARK: - Functions

    private var image: some View {
        BaseImage(
            imageName: imageName,
            imageSize: imageSize,
            imageWeight: imageWeight
        )
    }
}

// MARK: - Preview

#Preview {
    VStack {
        BaseButton(style: .plain, imageName: .plusCircle) {}
        BaseButton(style: .capsuled, imageName: .plusCircle) {}
        BaseButton(style: .capsuledLong, imageName: .plusCircle) {}
    }
}
