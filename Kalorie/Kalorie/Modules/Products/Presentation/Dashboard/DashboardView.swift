//
//  DashboardView.swift
//  Kalorie
//
//  Created by Josef Antoni on 05.06.2024.
//

import Foundation
import SwiftUI


struct DashboardView: View {
    
    // MARK: - Properties
    
    @State var foodItems = ["1", "2", "3"]
    @ObservedObject var viewModel: DashboardViewModel
    @State var showSheet = false

    
    // MARK: - Init
    
    init(viewModel: DashboardViewModel) {
        self.viewModel = viewModel
    }
    
    
    // MARK: - Body

    var body: some View {
        HStack {
            Spacer()
            Button(action: {
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


// MARK: - Preview

#Preview {
    DashboardView(viewModel: .demo)
}
