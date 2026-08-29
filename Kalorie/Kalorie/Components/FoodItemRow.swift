//
//  FoodItemRow.swift
//  Kalorie
//
//  Created by Josef Antoni on 12.08.2026.
//

import SwiftUI

struct FoodItemRow: View {

    // MARK: - Properties

    let item: FoodItemDomain
    let isFavourite: Bool

    // MARK: - Body

    var body: some View {
        let row = HStack {
            Text(item.displayName)
                .frame(maxWidth: .infinity, alignment: .leading)
            if isFavourite {
                BaseImage(imageName: .heartFill)
                    .foregroundStyle(.red)
                    .accessibilityHidden(true)
                    .contentTransition(.symbolEffect(.replace))
            }
        }
        if isFavourite {
            row.accessibilityLabel(Text(verbatim: "\(item.displayName), \(L10n.AddFood.sectionFavourites)"))
        } else {
            row
        }
    }
}

// MARK: - Preview

#Preview {
    List {
        FoodItemRow(
            item: FoodItemDomain(
                id: "1",
                czName: "Vejce",
                engName: "Egg",
                weight: 100,
                date: .now,
                energyKJ: 648,
                caloriesPerHundredGrams: 155,
                fat: 10,
                fatSaturated: 3,
                fatUnsaturatedFattyAcids: 3,
                carbohydrate: 1,
                carbohydratePureSugar: 0,
                fiber: 0,
                protein: 13,
                salt: 0.3
            ),
            isFavourite: true
        )
        FoodItemRow(
            item: FoodItemDomain(
                id: "2",
                czName: "Chléb",
                engName: "Bread",
                weight: 100,
                date: .now,
                energyKJ: 1050,
                caloriesPerHundredGrams: 250,
                fat: 3,
                fatSaturated: 1,
                fatUnsaturatedFattyAcids: 1,
                carbohydrate: 45,
                carbohydratePureSugar: 3,
                fiber: 4,
                protein: 8,
                salt: 1.2
            ),
            isFavourite: false
        )
    }
}
