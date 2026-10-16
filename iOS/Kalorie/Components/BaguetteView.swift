//
//  BaguetteView.swift
//  Kalorie
//
//  Created by Josef Antoni on 16.10.2026.
//

import SwiftUI

struct BaguetteView: View {

    // MARK: - Properties

    let length: CGFloat

    private var thickness: CGFloat { length * 0.22 }

    // MARK: - Body

    var body: some View {
        Capsule()
            .fill(Color.baguetteCrust)
            .overlay {
                HStack(spacing: length * 0.07) {
                    ForEach(0..<4, id: \.self) { _ in
                        Capsule()
                            .fill(Color.baguetteScore)
                            .frame(width: length * 0.14, height: thickness * 0.2)
                            .rotationEffect(.degrees(-55))
                    }
                }
            }
            .frame(width: length, height: thickness)
    }
}

#if DEBUG
// MARK: - Preview

#Preview {
    BaguetteView(length: 200)
}
#endif
