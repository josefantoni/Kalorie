//
//  AppConfigDTO.swift
//  Kalorie
//
//  Created by Josef Antoni on 21.10.2026.
//

import Foundation

struct AppConfigDTO: Codable {

    // MARK: - Properties

    let minVersionIos: String?
    let minVersionAndroid: String?

    // MARK: - Coding keys

    enum CodingKeys: String, CodingKey {
        case minVersionIos = "min_version_ios"
        case minVersionAndroid = "min_version_android"
    }
}

// MARK: - Decoding

extension AppConfigDTO {

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        self.init(
            minVersionIos: try? container.decodeIfPresent(String.self, forKey: .minVersionIos),
            minVersionAndroid: try? container.decodeIfPresent(String.self, forKey: .minVersionAndroid)
        )
    }
}
