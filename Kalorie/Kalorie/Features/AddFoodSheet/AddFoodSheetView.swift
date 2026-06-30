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
        NavigationStack {
            VStack(spacing: 0) {
                ZStack {
                    ZStack {
                        if !viewModel.isAddNewItemVisible {
                            addFoodItem
                        } else {
                            addCustomFoodItem
                                .rotation3DEffect(Angle(degrees: 180), axis: (x: 0, y: 1.0, z: 0))
                        }
                    }
                    .rotation3DEffect(
                        .degrees(viewModel.isAddNewItemVisible ? 180 : 0),
                        axis: (x: 0, y: 1, z: 0)
                    )
                    .animation(.default, value: viewModel.isAddNewItemVisible)

                    startDataScannerIfPossible
                }
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
                    dismissButton: Alert.Button.default(Text(L10n.Common.ok))
                )
            }
            .task { await viewModel.onAppear() }
            .onChange(of: viewModel.shouldDismiss) { newValue in
                if newValue { dismiss() }
            }
            .toolbar {
                DismissToolbarItem()
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

    var addFoodItem: some View {
        List {
            Section {
                HStack {
                    TextField(L10n.AddFood.searchPlaceholder, text: $viewModel.searchText)
                    BaseButton(
                        style: .plain,
                        imageName: .barCode,
                        imageSize: .medium
                    ) {
                        if DataScannerViewController.isSupported && DataScannerViewController.isAvailable {
                            viewModel.isAddNewItemVisible.toggle()
                            viewModel.isScannerVisible.toggle()
                        } else {
                            viewModel.alertTitle = L10n.AddFood.cameraPermissionAlert
                            viewModel.isAlertVisible.toggle()
                        }
                    }
                }
            }
            Section(header: Text(L10n.AddFood.sectionSearchResults)) {
                ForEach(viewModel.foodsFiltered, id: \.id) {
                    Text($0.name)
                }
            }
        }
    }

    var addCustomFoodItem: some View {
        List {
            Section(
                header: Text(L10n.AddFood.sectionNewItem),
                footer: footerView
            ) {
                BaseStringTextField(
                    placeholder: L10n.AddFood.fieldBarcodePlaceholder,
                    title: L10n.AddFood.fieldBarcodeTitle,
                    text: $viewModel.formInput.scannedCode
                )
                BaseStringTextField(
                    placeholder: L10n.AddFood.fieldNamePlaceholder,
                    title: L10n.AddFood.fieldNameTitle,
                    text: $viewModel.formInput.name
                )
                BaseDoubleTextField(
                    title: L10n.AddFood.fieldWeight,
                    weight: $viewModel.formInput.weightOfProduct
                )
                BaseDoubleTextField(
                    title: L10n.AddFood.fieldCaloriesPer100g,
                    weight: $viewModel.formInput.caloriesPerHundredGrams
                )
                BaseDoubleTextField(
                    title: L10n.AddFood.fieldProtein,
                    weight: $viewModel.formInput.protein
                )
                BaseDoubleTextField(
                    title: L10n.AddFood.fieldCarbs,
                    weight: $viewModel.formInput.carbohydrate
                )
                BaseDoubleTextField(
                    title: L10n.AddFood.fieldCarbsSugar,
                    weight: $viewModel.formInput.carbohydratePureSugar
                )
                BaseDoubleTextField(
                    title: L10n.AddFood.fieldFat,
                    weight: $viewModel.formInput.fat
                )
                BaseDoubleTextField(
                    title: L10n.AddFood.fieldFatUnsaturated,
                    weight: $viewModel.formInput.fatUnsaturatedFattyAcids
                )
                BaseDoubleTextField(
                    title: L10n.AddFood.fieldSalt,
                    weight: $viewModel.formInput.salt
                )
            }
        }
    }

    @ViewBuilder var footerView: some View {
        HStack {
            Button {
                Task { await viewModel.onCreateFoodItem() }
            } label: {
                Text(L10n.AddFood.buttonAdd)
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
