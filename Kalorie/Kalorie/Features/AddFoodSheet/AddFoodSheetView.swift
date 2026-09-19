//
//  AddFoodSheetView.swift
//  Kalorie
//
//  Created by Josef Antoni on 17.06.2024.
//

import Foundation
import SwiftUI
import UIKit
import VisionKit

struct AddFoodSheetView: View {

    // MARK: - Properties

    @StateObject var viewModel: AddFoodSheetViewModel
    @Environment(\.dismiss) var dismiss
    @Environment(\.scenePhase) private var scenePhase
    private let makeFoodQuantityView: (FoodItemDomain, Bool, MyCreatedMealDomain?, @escaping () -> Void, @escaping (String, Bool) -> Void, @escaping (MyCreatedMealDomain) -> Void) -> FoodQuantityView
    private let makeMealEditorView: (@escaping () -> Void) -> MyCreatedMealEditorView
    private let makeEditMealView: (MyCreatedMealDomain, @escaping () -> Void) -> MyCreatedMealEditorView

    // MARK: - Init

    init(
        viewModel: AddFoodSheetViewModel,
        makeFoodQuantityView: @escaping (FoodItemDomain, Bool, MyCreatedMealDomain?, @escaping () -> Void, @escaping (String, Bool) -> Void, @escaping (MyCreatedMealDomain) -> Void) -> FoodQuantityView,
        makeMealEditorView: @escaping (@escaping () -> Void) -> MyCreatedMealEditorView,
        makeEditMealView: @escaping (MyCreatedMealDomain, @escaping () -> Void) -> MyCreatedMealEditorView
    ) {
        self._viewModel = StateObject(wrappedValue: viewModel)
        self.makeFoodQuantityView = makeFoodQuantityView
        self.makeMealEditorView = makeMealEditorView
        self.makeEditMealView = makeEditMealView
    }

    // MARK: - Body

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                Picker("", selection: modeBinding) {
                    ForEach(AddFoodSheetMode.allCases) { mode in
                        Text(mode.title).tag(mode)
                    }
                }
                .pickerStyle(.segmented)
                .padding(.horizontal, 16)
                .padding(.top, 8)
                .padding(.bottom, 16)

                switch viewModel.mode {
                case .search:
                    addFoodItem
                case .newItem:
                    newItemPrompt
                case .createMeal:
                    makeMealEditorView {
                        Task { await viewModel.onMyCreatedMealSaved() }
                    }
                }
            }
            .navigationTitle(navigationTitle)
            .navigationBarTitleDisplayMode(.inline)
            .loader(viewModel.state.isLoading)
            .fullScreenCover(isPresented: $viewModel.isScannerVisible) {
                BarcodeScannerOverlay(
                    scannedCode: $viewModel.lastScannedBarcode,
                    isSearching: viewModel.isBarcodeSearchLoading
                ) {
                    viewModel.isScannerVisible = false
                }
            }
            .fullScreenCover(isPresented: $viewModel.isNutritionLabelCameraVisible, onDismiss: {
                viewModel.onNutritionLabelCameraDismissed()
            }) {
                NutritionLabelCameraView(
                    isRecognizing: viewModel.isRecognizingNutritionLabel,
                    hint: viewModel.nutritionLabelCameraHint,
                    onCaptured: { image, liveBarcode in
                        await viewModel.onNutritionLabelCaptured(image, liveBarcode: liveBarcode)
                    },
                    onClose: { viewModel.isNutritionLabelCameraVisible = false }
                )
            }
            .fullScreenCover(isPresented: $viewModel.isBarcodeRescanVisible) {
                BarcodeScannerOverlay(scannedCode: $viewModel.rescannedBarcode, isSearching: false) {
                    viewModel.isBarcodeRescanVisible = false
                }
            }
            .onChange(of: viewModel.rescannedBarcode) {
                viewModel.onBarcodeRescanned()
            }
            .alert(item: $viewModel.alertItem) { item in
                Alert(
                    title: Text(item.title),
                    message: item.message.map(Text.init),
                    dismissButton: Alert.Button.default(Text(L10n.Common.ok))
                )
            }
            .alert(L10n.AddFood.confirmWithdrawSubmission, isPresented: $viewModel.isSubmissionDeleteConfirmationVisible) {
                Button(L10n.Common.buttonNo, role: .cancel) {}
                Button(L10n.Common.buttonYes, role: .destructive) {
                    Task { await viewModel.onDeleteSubmissionConfirmed() }
                }
            }
            .task { await viewModel.onAppear() }
            .task(id: viewModel.searchText) { await viewModel.onSearchTextChanged() }
            .task(id: viewModel.lastScannedBarcode) {
                guard !viewModel.lastScannedBarcode.isEmpty else { return }
                await viewModel.onBarcodeScanned()
            }
            .onChange(of: viewModel.shouldDismiss) {
                if viewModel.shouldDismiss { dismiss() }
            }
            .onChange(of: scenePhase) { _, newPhase in
                if newPhase == .active {
                    viewModel.onScenePhaseActive(isCameraAvailable: DataScannerViewController.isAvailable)
                }
            }
            .navigationDestination(isPresented: $viewModel.isPushedToQuantityView) {
                if let item = viewModel.selectedFoodItem {
                    let meal = viewModel.myCreatedMeal(for: item)
                    let quantityView = makeFoodQuantityView(item, viewModel.isFavourite(item), meal, viewModel.onFoodConsumedSaved, { id, isFavourite in
                        viewModel.onFavouriteChanged(id: id, isFavourite: isFavourite, item: item)
                    }) { updatedMeal in
                        viewModel.onMyCreatedMealUpdated(updatedMeal)
                    }
                    if let meal {
                        quantityView.mealActions(
                            makeEditorView: {
                                makeEditMealView(meal) {
                                    Task { await viewModel.onMyCreatedMealSaved() }
                                }
                            }
                        ) {
                            Task { await viewModel.onDeleteMealConfirmed(meal) }
                        }
                    } else {
                        quantityView
                    }
                }
            }
            .navigationDestination(isPresented: $viewModel.isReviewPushed) {
                newItemReviewView
            }
            .alert(L10n.MyCreatedMeal.confirmDelete, isPresented: $viewModel.isMealDeleteConfirmationVisible) {
                Button(L10n.Common.buttonNo, role: .cancel) {}
                Button(L10n.Common.buttonYes, role: .destructive) {
                    Task { await viewModel.onDeleteMealConfirmed() }
                }
            }
            .toolbar {
                DismissToolbarItem()
            }
            .toolbarBackground(.hidden, for: .navigationBar)
            .background(Color(.systemGroupedBackground))
        }
    }

    // MARK: - Functions

    func onSelectResultRow(_ item: FoodItemDomain) {
        if viewModel.submissionStatus(for: item) == .rejected {
            viewModel.onSelectRejectedSubmission(item)
        } else {
            viewModel.onSelectFoodItem(item)
        }
    }

    private var navigationTitle: String {
        switch viewModel.mode {
        case .search:
            L10n.AddFood.navigationTitleSearch
        case .newItem:
            L10n.AddFood.navigationTitleNewItem
        case .createMeal:
            ""
        }
    }

    private var modeBinding: Binding<AddFoodSheetMode> {
        Binding(
            get: { viewModel.mode },
            set: { viewModel.onModeSelected($0) }
        )
    }

    var addFoodItem: some View {
        List {
            Section {
                HStack {
                    TextField(viewModel.searchPlaceholder, text: $viewModel.searchText)
                    BaseButton(
                        style: .plain,
                        imageName: .barCode,
                        imageSize: .basic
                    ) {
                        if DataScannerViewController.isSupported && DataScannerViewController.isAvailable {
                            viewModel.onScannerButtonTapped()
                        } else {
                            viewModel.alertItem = AlertItem(title: L10n.AddFood.cameraPermissionAlert)
                        }
                    }
                }
            }
            if viewModel.searchText.isEmpty && !viewModel.favouriteFoods.isEmpty {
                Section(header: Text(L10n.AddFood.sectionFavourites)) {
                    ForEach(viewModel.favouriteFoods, id: \.id) { item in
                        FoodItemRow(item: item, isFavourite: true)
                            .onTapGesture {
                                Task { await viewModel.onSelectFavouriteFood(item) }
                            }
                    }
                }
            }
            if viewModel.searchText.isEmpty && !viewModel.myCreatedMeals.isEmpty {
                Section(header: Text(L10n.AddFood.sectionMyCreatedMeals)) {
                    ForEach(viewModel.myCreatedMeals, id: \.id) { meal in
                        HStack {
                            FoodItemRow(item: meal.asFoodItem(), isFavourite: false)
                            Image(systemName: "chevron.right")
                                .font(.footnote.weight(.semibold))
                                .foregroundStyle(.tertiary)
                        }
                        .contentShape(Rectangle())
                        .onTapGesture {
                            viewModel.onSelectFoodItem(meal.asFoodItem())
                        }
                        .swipeActions(edge: .trailing) {
                            Button(role: .destructive) {
                                viewModel.onDeleteMealRequested(meal)
                            } label: {
                                Image(systemName: "trash")
                            }
                        }
                    }
                }
            }
            if viewModel.searchText.isEmpty && !viewModel.mySubmissions.isEmpty {
                Section(header: Text(L10n.AddFood.sectionMySubmissions)) {
                    ForEach(viewModel.mySubmissions, id: \.id) { submission in
                        FoodItemRow(item: submission.item, isFavourite: false, submissionStatus: submission.status)
                            .onTapGesture {
                                viewModel.onSelectSubmission(submission)
                            }
                            .swipeActions(edge: .trailing) {
                                Button(role: .destructive) {
                                    viewModel.onDeleteSubmissionRequested(submission)
                                } label: {
                                    Image(systemName: "trash")
                                }
                            }
                    }
                }
            }
            if !viewModel.displayedResults.isEmpty || !viewModel.searchText.isEmpty {
                Section(header: Text(L10n.AddFood.sectionExternalResults)) {
                    if !viewModel.displayedResults.isEmpty {
                        ForEach(viewModel.displayedResults, id: \.id) { item in
                            FoodItemRow(item: item, isFavourite: viewModel.isFavourite(item), submissionStatus: viewModel.submissionStatus(for: item))
                                .onTapGesture {
                                    onSelectResultRow(item)
                                }
                        }
                    } else if viewModel.isExternalSearchLoading {
                        ProgressView()
                            .frame(maxWidth: .infinity)
                    } else {
                        ForEach(viewModel.externalFoodItems, id: \.id) { item in
                            Text(item.displayName)
                                .frame(maxWidth: .infinity, alignment: .leading)
                                .contentShape(Rectangle())
                                .onTapGesture {
                                    viewModel.onSelectFoodItem(item)
                                }
                        }
                    }
                }
            }
        }
        .contentMargins(.top, 0, for: .scrollContent)
    }

    var newItemPrompt: some View {
        Group {
            switch viewModel.cameraAccess {
            case .authorized, .notDetermined:
                VStack(spacing: 24) {
                    Button {
                        Task { await viewModel.onNutritionLabelPromptTapped() }
                    } label: {
                        VStack(spacing: 16) {
                            Image(systemName: BaseImageName.camera.rawValue)
                                .font(.title2)
                                .fontWeight(.semibold)
                                .foregroundStyle(.white)
                                .padding(20)
                                .background(Color.accentColor)
                                .clipShape(.circle)
                            Text(L10n.AddFood.nutritionLabelPromptBody)
                                .font(.subheadline)
                                .foregroundStyle(.secondary)
                                .multilineTextAlignment(.center)
                        }
                    }
                    .buttonStyle(.plain)
                    addManuallyButton(title: L10n.AddFood.buttonAddManually)
                }
                .padding(32)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .contentShape(Rectangle())
            case .denied:
                VStack(spacing: 16) {
                    promptIcon
                    Text(L10n.AddFood.nutritionLabelDeniedMessage)
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                        .multilineTextAlignment(.center)
                    Button(L10n.AddFood.buttonOpenSettings) {
                        openSettings()
                    }
                    .buttonStyle(.borderedProminent)
                    addManuallyButton(title: L10n.AddFood.buttonAddManually)
                }
                .padding(32)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
            case .unsupported:
                VStack(spacing: 16) {
                    Text(L10n.AddFood.nutritionLabelUnsupportedMessage)
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                        .multilineTextAlignment(.center)
                    addManuallyButton(title: L10n.AddFood.buttonAddFoodManually)
                }
                .padding(32)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
            }
        }
    }

    private var promptIcon: some View {
        Image(systemName: BaseImageName.camera.rawValue)
            .font(.system(size: .extraLarge))
            .foregroundStyle(.secondary)
    }

    private func addManuallyButton(title: String) -> some View {
        Button(title) {
            viewModel.onAddManuallyTapped()
        }
        .font(.subheadline)
    }

    private func openSettings() {
        guard let url = URL(string: UIApplication.openSettingsURLString) else { return }
        UIApplication.shared.open(url)
    }

    var newItemReviewView: some View {
        VStack(spacing: 0) {
            List {
                if let rejectionReason = viewModel.rejectionReasonBeingEdited {
                    Section {
                        Text(L10n.AddFood.submissionRejectedReason(reason: rejectionReason))
                            .foregroundStyle(.red)
                    }
                }
                FoodItemFormSections(
                    formInput: $viewModel.formInput,
                    highlightedFields: viewModel.recognizedFields,
                    barcodeRow: viewModel.isEditingSubmission ? .locked : .editable {
                        if DataScannerViewController.isSupported && DataScannerViewController.isAvailable {
                            viewModel.onBarcodeRescanTapped()
                        } else {
                            viewModel.alertItem = AlertItem(title: L10n.AddFood.cameraPermissionAlert)
                        }
                    },
                    // Unlabeled trailing closure would be matched backward past the omitted onNutritionLabelScanTapped.
                    // swiftlint:disable:next trailing_closure
                    onFieldEdited: { field in
                        viewModel.onFormFieldEdited(field)
                    }
                )
            }
            .contentMargins(.top, 0, for: .scrollContent)
            addButton
                .padding(.horizontal)
                .padding(.vertical, 12)
                .background(Color(.secondarySystemBackground))
        }
        .navigationTitle(L10n.AddFood.navigationTitleNewItem)
        .navigationBarTitleDisplayMode(.inline)
        .alert(L10n.AddFood.submissionSubmitted, isPresented: $viewModel.isSubmissionConfirmationVisible) {
            Button(L10n.Common.ok) { viewModel.onSubmissionConfirmationDismissed() }
        } message: {
            Text(L10n.AddFood.submissionSubmittedMessage)
        }
        .alert(L10n.AddFood.confirmMissingBarcode, isPresented: $viewModel.isMissingBarcodeConfirmationVisible) {
            Button(L10n.Common.buttonNo, role: .cancel) {}
            Button(L10n.Common.buttonYes) {
                Task { await viewModel.onMissingBarcodeConfirmed() }
            }
        }
    }

    var addButton: some View {
        Button {
            Task { await viewModel.onCreateFoodItem() }
        } label: {
            Text(L10n.AddFood.buttonAdd)
                .frame(maxWidth: .infinity)
                .frame(height: 35)
                .font(.system(size: .basic, weight: .bold))
        }
        .buttonStyle(.borderedProminent)
    }
}

// MARK: - Preview

#Preview {
    AddFoodSheetView(
        viewModel: AddFoodSheetViewModel(
            searchFoodItems: SearchFoodItemsUseCaseFake(),
            submitFoodItem: SubmitFoodItemUseCaseFake(),
            fetchMySubmissions: FetchMySubmissionsUseCaseFake(),
            updateMySubmission: UpdateMySubmissionUseCaseFake(),
            deleteMySubmission: DeleteMySubmissionUseCaseFake(),
            searchFoodExternally: SearchFoodExternallyUseCaseFake(),
            fetchFoodItemByBarcode: FetchFoodItemByBarcodeUseCaseFake(),
            fetchFoodByBarcodeExternally: FetchFoodByBarcodeExternallyUseCaseFake(),
            fetchFavouriteFoods: FetchFavouriteFoodsUseCaseFake(),
            refreshFavouriteFood: RefreshFavouriteFoodUseCaseFake(),
            fetchMyCreatedMeals: FetchMyCreatedMealsUseCaseFake(),
            deleteMyCreatedMeal: DeleteMyCreatedMealUseCaseFake(),
            recognizeNutritionLabel: RecognizeNutritionLabelUseCaseFake(),
            cameraAuthorizationProvider: CameraAuthorizationProviderFake()
        ),
        makeFoodQuantityView: { item, isFavourite, meal, onSaved, onFavouriteChanged, onMealUpdated in
            FoodQuantityView(
                viewModel: FoodQuantityViewModel(
                    item: item,
                    saveFoodConsumed: SaveFoodConsumedUseCaseFake(),
                    fetchMealTypes: FetchMealTypesUseCaseFake(),
                    selectedDate: .now,
                    mealTypes: [],
                    isFavourite: isFavourite,
                    addFavouriteFood: AddFavouriteFoodUseCaseFake(),
                    removeFavouriteFood: RemoveFavouriteFoodUseCaseFake(),
                    fetchFoodItemPersonalPortions: FetchFoodItemPersonalPortionsUseCaseFake(),
                    saveFoodItemPersonalPortions: SaveFoodItemPersonalPortionsUseCaseFake(),
                    fetchMyFoodItemReport: FetchMyFoodItemReportUseCaseFake(),
                    submitFoodItemReport: SubmitFoodItemReportUseCaseFake(),
                    meal: meal,
                    updateMyCreatedMeal: UpdateMyCreatedMealUseCaseFake(),
                    onSaved: onSaved,
                    onMealUpdated: onMealUpdated,
                    onFavouriteChanged: onFavouriteChanged,
                    quantity: meal != nil ? item.weight : (item.portions.isEmpty ? 100 : 1),
                    unit: meal != nil ? .grams : FoodQuantityViewModel.defaultUnit(for: item)
                )
            )
        },
        makeMealEditorView: { onSaved in
            MyCreatedMealEditorView(
                viewModel: MyCreatedMealEditorViewModel(
                    searchFoodItems: SearchFoodItemsUseCaseFake(),
                    searchFoodExternally: SearchFoodExternallyUseCaseFake(),
                    fetchFoodItemByBarcode: FetchFoodItemByBarcodeUseCaseFake(),
                    fetchFoodByBarcodeExternally: FetchFoodByBarcodeExternallyUseCaseFake(),
                    fetchFoodItemsByIds: FetchFoodItemsByIdsUseCaseFake(),
                    createMyCreatedMeal: CreateMyCreatedMealUseCaseFake(),
                    updateMyCreatedMeal: UpdateMyCreatedMealUseCaseFake(),
                    onSaved: onSaved
                )
            )
        }
    ) { _, onSaved in
        MyCreatedMealEditorView(
            viewModel: MyCreatedMealEditorViewModel(
                searchFoodItems: SearchFoodItemsUseCaseFake(),
                searchFoodExternally: SearchFoodExternallyUseCaseFake(),
                fetchFoodItemByBarcode: FetchFoodItemByBarcodeUseCaseFake(),
                fetchFoodByBarcodeExternally: FetchFoodByBarcodeExternallyUseCaseFake(),
                fetchFoodItemsByIds: FetchFoodItemsByIdsUseCaseFake(),
                createMyCreatedMeal: CreateMyCreatedMealUseCaseFake(),
                updateMyCreatedMeal: UpdateMyCreatedMealUseCaseFake(),
                onSaved: onSaved
            )
        )
    }
}
