//
//  AddFoodSheetView.swift
//  Kalorie
//
//  Created by Josef Antoni on 17.06.2024.
//

import Foundation
import SwiftUI
import VisionKit


struct AddFoodSheetView: View {
    
    // MARK: - Properties
    
    @Environment(\.dismiss) var dismiss
    @ObservedObject var viewModel: AddFoodSheetViewModel
    
    
    // MARK: - Init

    init(withBarcodeScan: Bool, isAddNewItemVisible: Bool = false) {
        let state = AddFoodSheetViewState(
            isAddNewItemVisible: isAddNewItemVisible,
            isScannerVisible: withBarcodeScan
        )
        self.viewModel = AddFoodSheetViewModel(state: state)
    }
    
    
    // MARK: - Body

    var body: some View {
        VStack(spacing: 0) {
            HStack(spacing: 0) {
                Spacer()
                Button {
                    dismiss()
                } label: {
                    Image(systemName: "xmark")
                        .font(.largeTitle)
                        .fontWeight(.light)
                }
                .padding([.top, .trailing])
            }
            
            ZStack {
                if !viewModel.state.isAddNewItemVisible {
                    VStack {
                        HStack {
                            Button {
                                viewModel.state.isAddNewItemVisible.toggle()
                            } label: {
                                Image(systemName: "plus.circle")
                                    .font(.system(size: 30))
                                    .frame(maxWidth: .infinity, minHeight: 50)
                            }
                            .background(.green)
                            .clipShape(Capsule())

                            Button {
                                viewModel.state.isAddNewItemVisible.toggle()
                                viewModel.state.isScannerVisible.toggle()
                            } label: {
                                Image(systemName: "barcode.viewfinder")
                                    .font(.system(size: 30))
                                    .frame(maxWidth: .infinity, minHeight: 50)
                            }
                            .background(.green)
                            .clipShape(Capsule())

                        }
                        .padding(.horizontal, 15)
                        
                        List {
                            ForEach($viewModel.state.foodsOnServer, id: \.id) { food in
                                Text(food.name.wrappedValue)
                            }
                        }
                    }
                } else {
                    VStack {
                        HStack {
                            TextField("Kód potraviny", text: $viewModel.state.scannedCode ?? "")
                                .keyboardType(.numberPad)
                                .frame(maxWidth: .infinity)
                            
                            Button {

                            } label: {
                                Image(systemName: "barcode.viewfinder")
                                    .foregroundColor(.green)
                            }

                        }

                        TextField("Název potraviny", text: $viewModel.state.name ?? "")
                        VStack {
                            TextField("hmotnost", value: $viewModel.state.weightOfProduct, format: .number)
                            TextField("kalorie na 100 gramů", value: $viewModel.state.caloriesPerHundredGrams, format: .number)

                            TextField(
                                "$caloriesPerHundredGrams",
                                value: $viewModel.state.caloriesPerHundredGrams,
                                format: .number
                            )
                            TextField(
                                "$fat",
                                value: $viewModel.state.fat,
                                format: .number
                            )
                            TextField(
                                "$fatUnsaturatedFattyAcids",
                                value: $viewModel.state.fatUnsaturatedFattyAcids,
                                format: .number
                            )
                            TextField(
                                "$carbohydrate",
                                value: $viewModel.state.carbohydrate,
                                format: .number
                            )
                            TextField(
                                "$carbohydratePureSugar",
                                value: $viewModel.state.carbohydratePureSugar,
                                format: .number
                            )
                            TextField(
                                "$protein",
                                value: $viewModel.state.protein,
                                format: .number
                            )
                            TextField(
                                "$salt",
                                value: $viewModel.state.salt,
                                format: .number
                            )
                        }
                        .padding()
                        .keyboardType(.numberPad)
                        
                        Spacer()
                        Button(action: {
                            do {
                                try viewModel.createNewFoodRecord(
                                    id: viewModel.state.scannedCode ?? "",
                                    name: viewModel.state.name ?? "",
                                    weightOfProduct: viewModel.state.weightOfProduct ?? 0,
                                    caloriesPerHundredGrams: viewModel.state.caloriesPerHundredGrams ?? 0,
                                    fat: viewModel.state.fat ?? 0,
                                    fatUnsaturatedFattyAcids: viewModel.state.fatUnsaturatedFattyAcids ?? 0,
                                    carbohydrate: viewModel.state.carbohydrate ?? 0,
                                    carbohydratePureSugar: viewModel.state.carbohydratePureSugar ?? 0,
                                    protein: viewModel.state.protein ?? 0,
                                    salt: viewModel.state.salt ?? 0
                                )
                                viewModel.state.isAddNewItemVisible.toggle()
                            } catch let error {
                                viewModel.createNewFoodRecordErrorHandler(error)
                                viewModel.state.isAddNewItemVisible.toggle()
                            }
                        }, label: {
                            Text("Vytvořit")
                        }).padding()
                    }
                }
                if viewModel.state.isScannerVisible {
                    startDataScanner
                }
            }
        }
        .alert(isPresented: $viewModel.state.isAlertVisible) {
            Alert(
                title: Text(viewModel.state.alertTitle),
                dismissButton: Alert.Button.default(Text("Dobrá"))
            )
        }
        .onAppear {
            viewModel.refreshAvailableFoodItems()
        }
    }
    
    @ViewBuilder @MainActor var startDataScanner: some View {
        if DataScannerViewController.isSupported && DataScannerViewController.isAvailable {
            ZStack(alignment: .bottom) {
//                DataScannerRepresentable(shouldStartScanning: $isScannerVisible, scannedCode: $scannedCode)
            }
        } else if !DataScannerViewController.isSupported {
            Text("It looks like this device doesn't support the DataScannerViewController")
        } else {
            Text("It appears your camera may not be available")
        }
    }
}


// MARK: - Preview

#Preview {
    AddFoodSheetView(withBarcodeScan: false)
}


public extension Binding {
    /// Create a non-optional version of an optional `Binding` with a default value
    /// - Parameters:
    ///   - lhs: The original `Binding<Value?>` (binding to an optional value)
    ///   - rhs: The default value if the original `wrappedValue` is `nil`
    /// - Returns: The `Binding<Value>` (where `Value` is non-optional)
    static func ?? (lhs: Binding<Value?>, rhs: Value) -> Binding<Value> {
        Binding {
            lhs.wrappedValue ?? rhs
        } set: {
            lhs.wrappedValue = $0
        }
    }
}
