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
    
    @ObservedObject var viewModel: AddFoodSheetViewModel
    @Environment(\.dismiss) var dismiss
    
    
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
        NavigationView {
            VStack(spacing: 0) {
                ZStack {
                    if !viewModel.state.isAddNewItemVisible {
                        addFoodItem
                    } else {
                        addCustomFoodItem
                            .rotation3DEffect(Angle(degrees: 180), axis: (x: 0, y: 1.0, z: 0))
                    }
                    startDataScannerIfPossible
                }
                .rotation3DEffect(
                    .degrees(viewModel.state.isAddNewItemVisible ? 180 : 0),
                    axis: (x: 0, y: 1, z: 0)
                )
                .animation(.default, value: viewModel.state.isAddNewItemVisible)
            }
            .safeAreaInset(edge: VerticalEdge.bottom) {
                HStack {
                    Spacer()
                    Button {
                        viewModel.state.isAddNewItemVisible.toggle()
                    } label: {
                        BaseImage(
                            imageName: .carrotFill,
                            imageSize: .mediumPlus
                        )
                        .padding(.all, 5)
                    }
                    .clipShape(Circle())
                    .buttonStyle(.borderedProminent)
                    .padding([.trailing, .bottom], 15)
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
            .toolbar {
                dismissButton
            }
            .background(
                Color(.secondarySystemBackground)
            )
        }
    }
    
    @ViewBuilder @MainActor var startDataScannerIfPossible: some View {
        if viewModel.state.isScannerVisible && DataScannerViewController.isSupported && DataScannerViewController.isAvailable {
            ZStack(alignment: .bottom) {
                DataScannerRepresentable(shouldStartScanning: $viewModel.state.isScannerVisible, scannedCode: $viewModel.state.scannedCode)
            }
        }
    }
    
    var dismissButton: ToolbarItem<(), some View> {
        ToolbarItem(placement: .topBarTrailing) {
            BaseButton(
                style: .plain,
                imageName: .close,
                imageSize: .basicPlus
            ) {
                dismiss()
            }
            .padding(.top, 10)
        }
    }
    
    var addFoodItem: some View {
        List {
            Section {
                HStack {
                    TextField("Vyhledávané jídlo", text: $viewModel.state.searchedFood)
                    BaseButton(
                        style: .plain,
                        imageName: .barCode,
                        imageSize: .medium
                    ) {
                        if DataScannerViewController.isSupported && DataScannerViewController.isAvailable {
                            viewModel.state.isAddNewItemVisible.toggle()
                            viewModel.state.isScannerVisible.toggle()
                        } else {
                            viewModel.state.alertTitle = "Povolte v nastavení fotoaparát, nemůžeme bez něj skenovat čárový kód. 🙁"
                            viewModel.state.isAlertVisible.toggle()
                        }
                    }
                }
            }
            Section(header: Text("Vyhledaná jídla")) {
                ForEach(viewModel.state.foodsFiltered, id: \.id) {
                    Text($0.name)
                }
            }
        }
    }
    
    var addCustomFoodItem: some View {
        List {
            Section(
                header: Text("Rozvržení jídel"),
                footer: footerView
            ) {
                BaseStringTextField(
                    placeholder: "321321...12345",
                    title: "Čárový kód potraviny",
                    text: $viewModel.state.scannedCode
                )
                BaseStringTextField(
                    placeholder: "Tvaroh nízkotučný",
                    title: "Název potraviny",
                    text: $viewModel.state.name
                )
                BaseDoubleTextField(
                    title: "Hmotnost",
                    weight: $viewModel.state.weightOfProduct
                )
                BaseDoubleTextField(
                    title: "Kalorie na 100 gramů",
                    weight: $viewModel.state.caloriesPerHundredGrams
                )
                BaseDoubleTextField(
                    title: "Bílkoviny",
                    weight: $viewModel.state.protein
                )
                BaseDoubleTextField(
                    title: "Sacharidy",
                    weight: $viewModel.state.carbohydrate
                )
                BaseDoubleTextField(
                    title: "z toho cukry",
                    weight: $viewModel.state.carbohydratePureSugar
                )
                BaseDoubleTextField(
                    title: "Tuky",
                    weight: $viewModel.state.fat
                )
                BaseDoubleTextField(
                    title: "z toho nenasycené",
                    weight: $viewModel.state.fatUnsaturatedFattyAcids
                )
                BaseDoubleTextField(
                    title: "Sůl",
                    weight: $viewModel.state.salt
                )
            }
        }
    }
    
    @ViewBuilder var footerView: some View {
        HStack {
            Button {
                do {
                    try viewModel.createNewFoodRecord(
                        id: viewModel.state.scannedCode,
                        name: viewModel.state.name,
                        weightOfProduct: viewModel.state.weightOfProduct,
                        caloriesPerHundredGrams: viewModel.state.caloriesPerHundredGrams,
                        fat: viewModel.state.fat,
                        fatUnsaturatedFattyAcids: viewModel.state.fatUnsaturatedFattyAcids,
                        carbohydrate: viewModel.state.carbohydrate,
                        carbohydratePureSugar: viewModel.state.carbohydratePureSugar,
                        protein: viewModel.state.protein,
                        salt: viewModel.state.salt
                    )
                    dismiss()
                } catch let error {
                    viewModel.createNewFoodRecordErrorHandler(error)
                }
            } label: {
                Text("Přidat")
                    .frame(maxWidth: .infinity)
                    .frame(height: 35)
            }
            .padding([.leading, .trailing], -20)
            .buttonStyle(.borderedProminent)
        }
        .padding(.top)
    }
}


// MARK: - Preview

#Preview {
    AddFoodSheetView(withBarcodeScan: false)
}
