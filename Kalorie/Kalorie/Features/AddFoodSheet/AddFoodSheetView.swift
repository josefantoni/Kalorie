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

    init(viewModel: AddFoodSheetViewModel) {
        self.viewModel = viewModel
    }

    // MARK: - Body

    var body: some View {
        NavigationView {
            VStack(spacing: 0) {
                ZStack {
                    if !viewModel.isAddNewItemVisible {
                        addFoodItem
                    } else {
                        addCustomFoodItem
                            .rotation3DEffect(Angle(degrees: 180), axis: (x: 0, y: 1.0, z: 0))
                    }
                    startDataScannerIfPossible
                }
                .rotation3DEffect(
                    .degrees(viewModel.isAddNewItemVisible ? 180 : 0),
                    axis: (x: 0, y: 1, z: 0)
                )
                .animation(.default, value: viewModel.isAddNewItemVisible)
            }
            .safeAreaInset(edge: VerticalEdge.bottom) {
                HStack {
                    Spacer()
                    Button {
                        viewModel.isAddNewItemVisible.toggle()
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
            .alert(isPresented: $viewModel.isAlertVisible) {
                Alert(
                    title: Text(viewModel.alertTitle),
                    dismissButton: Alert.Button.default(Text("Dobrá"))
                )
            }
            .task { await viewModel.onAppear() }
            .toolbar {
                dismissButton
            }
            .background(Color(.secondarySystemBackground))
        }
    }

    // MARK: - Functions

    @ViewBuilder @MainActor var startDataScannerIfPossible: some View {
        if viewModel.isScannerVisible && DataScannerViewController.isSupported && DataScannerViewController.isAvailable {
            ZStack(alignment: .bottom) {
                DataScannerRepresentable(
                    shouldStartScanning: $viewModel.isScannerVisible,
                    scannedCode: $viewModel.formInput.scannedCode
                )
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
                    TextField("Vyhledávané jídlo", text: $viewModel.searchText)
                    BaseButton(
                        style: .plain,
                        imageName: .barCode,
                        imageSize: .medium
                    ) {
                        if DataScannerViewController.isSupported && DataScannerViewController.isAvailable {
                            viewModel.isAddNewItemVisible.toggle()
                            viewModel.isScannerVisible.toggle()
                        } else {
                            viewModel.alertTitle = "Povolte v nastavení fotoaparát, nemůžeme bez něj skenovat čárový kód. 🙁"
                            viewModel.isAlertVisible.toggle()
                        }
                    }
                }
            }
            Section(header: Text("Vyhledaná jídla")) {
                ForEach(viewModel.foodsFiltered, id: \.id) {
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
                    text: $viewModel.formInput.scannedCode
                )
                BaseStringTextField(
                    placeholder: "Tvaroh nízkotučný",
                    title: "Název potraviny",
                    text: $viewModel.formInput.name
                )
                BaseDoubleTextField(
                    title: "Hmotnost",
                    weight: $viewModel.formInput.weightOfProduct
                )
                BaseDoubleTextField(
                    title: "Kalorie na 100 gramů",
                    weight: $viewModel.formInput.caloriesPerHundredGrams
                )
                BaseDoubleTextField(
                    title: "Bílkoviny",
                    weight: $viewModel.formInput.protein
                )
                BaseDoubleTextField(
                    title: "Sacharidy",
                    weight: $viewModel.formInput.carbohydrate
                )
                BaseDoubleTextField(
                    title: "z toho cukry",
                    weight: $viewModel.formInput.carbohydratePureSugar
                )
                BaseDoubleTextField(
                    title: "Tuky",
                    weight: $viewModel.formInput.fat
                )
                BaseDoubleTextField(
                    title: "z toho nenasycené",
                    weight: $viewModel.formInput.fatUnsaturatedFattyAcids
                )
                BaseDoubleTextField(
                    title: "Sůl",
                    weight: $viewModel.formInput.salt
                )
            }
        }
    }

    @ViewBuilder var footerView: some View {
        HStack {
            Button {
                Task {
                    do {
                        _ = try await viewModel.onCreateFoodItem()
                        dismiss()
                    } catch {
                        viewModel.onCreateFoodItemErrorHandler(error)
                    }
                }
            } label: {
                Text("Přidat")
                    .frame(maxWidth: .infinity)
                    .frame(height: 35)
                    .font(.system(size: .basic, weight: .bold))
            }
            .padding([.leading, .trailing], -20)
            .buttonStyle(.borderedProminent)
        }
        .padding(.top)
    }
}

// MARK: - Preview

#Preview {
    AddFoodSheetConfigurator().createView()
}
