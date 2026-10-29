//
//  BaseImage.swift
//  Kalorie
//
//  Created by Josef Antoni on 29.06.2024.
//

import Foundation
import SwiftUI

struct BaseImage: View {
    
    // MARK: - Properties

    let imageName: BaseImageName
    let imageSize: CGFloat
    let imageWeight: Font.Weight
    
    // MARK: - Init

    init(
        imageName: BaseImageName,
        imageSize: CGFloat = Constants.ImageSize.basic,
        imageWeight: Font.Weight = .thin
    ) {
        self.imageName = imageName
        self.imageSize = imageSize
        self.imageWeight = imageWeight
    }
    
    // MARK: - Body

    var body: some View {
        Image(systemName: imageName.rawValue)
            .font(.system(size: imageSize))
            .fontWeight(imageWeight)
    }
}

#if DEBUG
// MARK: - Preview

#Preview {
    BaseImage(imageName: .plusCircle, imageSize: Constants.ImageSize.extraLarge)
}
#endif
