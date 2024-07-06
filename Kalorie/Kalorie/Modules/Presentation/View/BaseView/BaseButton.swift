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
        Button {
            action()
        } label: {
            BaseImage(
                imageName: imageName,
                imageSize: imageSize,
                imageWeight: imageWeight
            )
            .if(style == .capsuledLong) { content in
                content
                    .frame(maxWidth: .infinity)
            }
        }
        .if(style == .capsuledLong || style == .capsuled) { content in
            content
                .clipShape(Capsule())
                .buttonStyle(.borderedProminent)
        }
        .if(style == .plain) { content in
            content
                .buttonStyle(.borderless)
        }
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
