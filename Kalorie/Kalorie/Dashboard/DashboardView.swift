//
//  DashboardView.swift
//  Kalorie
//
//  Created by Josef Antoni on 05.06.2024.
//

import Foundation
import SwiftUI

struct FoodItem: Hashable {
    let name: String

    init(_ name: String) {
        self.name = name
    }
}

struct DashboardView: View {
    @State var foodItems = ["1", "2", "3"]
    @State var showSheet = false

    var body: some View {
        HStack {
            Spacer()
            Button(action: {
                // TODO: rozvržení jídel
                showSheet.toggle()
            }, label: {
                Text("rozvržení jídel")
            })
            .sheet(isPresented: $showSheet) {
                MealTypeSheetView()
            }
        }
        .padding(.trailing)
        
        List($foodItems, id: \.self, editActions: .move) { food in
          // List item UI
            FoodItemView(food.wrappedValue)
        }
        // .environment(\.editMode, .constant(self.editMode ? EditMode.active : EditMode.inactive))
    }
}

#Preview {
    DashboardView()
}
