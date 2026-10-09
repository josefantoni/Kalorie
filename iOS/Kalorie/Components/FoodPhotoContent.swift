//
//  FoodPhotoContent.swift
//  Kalorie
//
//  Created by Josef Antoni on 08.10.2026.
//

import SwiftUI

struct FoodPhotoContent: View {

    // MARK: - Properties

    let photo: FoodItemFormPhoto
    var contentMode: ContentMode = .fill

    // MARK: - Body

    var body: some View {
        switch photo {
        case .none:
            Color.clear
        case .local(let data):
            if let image = UIImage(data: data) {
                Image(uiImage: image)
                    .resizable()
                    .aspectRatio(contentMode: contentMode)
            } else {
                Color.clear
            }
        case .remote(let url):
            RemoteFoodImage(url: url) { phase in
                switch phase {
                case .success(let image):
                    image
                        .resizable()
                        .aspectRatio(contentMode: contentMode)
                case .loading:
                    ProgressView()
                case .failure:
                    Image(systemName: BaseImageName.foodPlaceholder.rawValue)
                        .font(.largeTitle)
                        .foregroundStyle(.secondary)
                }
            }
        }
    }
}
