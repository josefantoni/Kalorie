//
//  MealTypeSheetView.swift
//  Kalorie
//
//  Created by Josef Antoni on 08.06.2024.
//

import Foundation
import SwiftUI

struct MealTypeSheetView: View {

    // MARK: - Properties

    @StateObject var viewModel: MealTypeSheetViewModel
    @StateObject private var myCreatedMealListViewModel: MyCreatedMealListViewModel
    @FocusState private var focusedField: Field?
    @State private var editMode: EditMode = .inactive

    private let makeEditorView: (MyCreatedMealDomain, @escaping () -> Void) -> MyCreatedMealEditorView

    private enum Field: Int, CaseIterable {
        case newMealName
    }

    // MARK: - Init

    init(
        viewModel: MealTypeSheetViewModel,
        myCreatedMealListViewModel: MyCreatedMealListViewModel,
        makeEditorView: @escaping (MyCreatedMealDomain, @escaping () -> Void) -> MyCreatedMealEditorView
    ) {
        _viewModel = StateObject(wrappedValue: viewModel)
        _myCreatedMealListViewModel = StateObject(wrappedValue: myCreatedMealListViewModel)
        self.makeEditorView = makeEditorView
    }

    // MARK: - Body

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                List {
                    Section(header: Text(L10n.MyCreatedMeal.listTitle)) {
                        if myCreatedMealListViewModel.meals.isEmpty && !myCreatedMealListViewModel.state.isLoading {
                            Text(L10n.MyCreatedMeal.listEmpty)
                                .font(.subheadline)
                                .foregroundStyle(.secondary)
                        }
                        ForEach(myCreatedMealListViewModel.meals, id: \.id) { meal in
                            NavigationLink {
                                makeEditorView(meal) {
                                    Task { await myCreatedMealListViewModel.onSaved() }
                                }
                            } label: {
                                HStack {
                                    Text(meal.name)
                                    Spacer()
                                    Text("\(Int(meal.ingredients.reduce(0) { $0 + $1.grams })) g")
                                        .foregroundStyle(.secondary)
                                }
                            }
                            .swipeActions(edge: .trailing) {
                                Button(role: .destructive) {
                                    myCreatedMealListViewModel.onDeleteRequested(meal)
                                } label: {
                                    Image(systemName: "trash")
                                }
                            }
                        }
                    }

                    Section(
                        header: HStack {
                            Text(L10n.MealTypeSheet.sectionMealLayout)
                            Spacer()
                            Button {
                                if editMode == .active {
                                    withAnimation { editMode = .inactive }
                                    Task { await viewModel.onSaveReorder() }
                                } else {
                                    withAnimation { editMode = .active }
                                }
                            } label: {
                                Text(editMode == .active ? L10n.MealTypeSheet.buttonEditDone : L10n.MealTypeSheet.buttonEdit)
                                    .padding(.horizontal, 14)
                                    .padding(.vertical, 8)
                            }
                            .background(Color(.systemGray5))
                            .clipShape(Capsule())
                        },
                        footer: Group {
                            if editMode == .active {
                                footerView
                                    .padding(.horizontal, -14)
                                    .padding(.top, 20)
                            }
                        }
                    ) {
                        ForEach($viewModel.mealTypes, id: \.id) { mealType in
                            MealTypeItemView(mealType.wrappedValue)
                                .swipeActions(edge: .trailing) {
                                    Button(role: .destructive) {
                                        if let index = viewModel.mealTypes.firstIndex(where: { $0.id == mealType.wrappedValue.id }) {
                                            Task { await viewModel.onDelete(at: index) }
                                        }
                                    } label: {
                                        Image(systemName: "trash")
                                    }
                                }
                        }
                        .onMove { from, to in
                            viewModel.onMove(from: from, to: to)
                        }
                    }
                }
                .environment(\.editMode, $editMode)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topTrailing)
            .toolbar {
                if editMode == .inactive {
                    DismissToolbarItem()
                }
            }
            .loader(viewModel.state.isLoading)
            .interactiveDismissDisabled(editMode == .active)
            .task { await myCreatedMealListViewModel.onAppear() }
            .alert(item: $viewModel.alertItem) { item in
                Alert(
                    title: Text(item.title),
                    message: item.message.map(Text.init),
                    dismissButton: Alert.Button.default(Text(L10n.Common.ok))
                )
            }
            .alert(item: $myCreatedMealListViewModel.alertItem) { item in
                Alert(
                    title: Text(item.title),
                    message: item.message.map(Text.init),
                    dismissButton: .default(Text(L10n.Common.ok))
                )
            }
            .alert(L10n.MyCreatedMeal.confirmDelete, isPresented: $myCreatedMealListViewModel.isDeleteConfirmationVisible) {
                Button(L10n.Common.buttonNo, role: .cancel) {}
                Button(L10n.Common.buttonYes, role: .destructive) {
                    Task { await myCreatedMealListViewModel.onDeleteConfirmed() }
                }
            }
        }
    }

    // MARK: - Functions

    @ViewBuilder var footerView: some View {
        if !viewModel.isAddFormVisible {
            BaseButton(
                style: .plain,
                imageName: .plusCircle,
                imageSize: .extraLarge
            ) {
                viewModel.onShowAddForm()
            }
            .frame(maxWidth: .infinity)
        } else {
            VStack {
                VStack {
                    TextField(L10n.MealTypeSheet.fieldNewMealPlaceholder, text: $viewModel.newMealName)
                        .padding(.horizontal, 20)
                        .font(.system(size: .smallPlus))
                        .padding(.top, 20)
                        .padding(.bottom, 10)
                        .focused($focusedField, equals: .newMealName)
                    Divider()

                    HStack {
                        DatePicker(L10n.MealTypeSheet.datePickerFrom, selection: $viewModel.newMealStart, displayedComponents: .hourAndMinute)
                            .datePickerStyle(GraphicalDatePickerStyle())
                            .onChange(of: viewModel.newMealStart) {
                                if viewModel.newMealStart >= viewModel.newMealEnd {
                                    viewModel.newMealEnd = viewModel.newMealStart.withAddedMinutes(minutes: 30)
                                }
                            }
                        Divider()
                        DatePicker(L10n.MealTypeSheet.datePickerTo, selection: $viewModel.newMealEnd, displayedComponents: .hourAndMinute)
                            .datePickerStyle(CompactDatePickerStyle())
                            .onChange(of: viewModel.newMealEnd) {
                                if viewModel.newMealStart >= viewModel.newMealEnd {
                                    viewModel.newMealEnd = viewModel.newMealStart.withAddedMinutes(minutes: 30)
                                }
                            }
                    }
                    .padding(.horizontal, 20)
                    .padding(.top, 10)
                    .font(.system(size: .basic))
                }
                .padding(.bottom, 20)
                .background(Color(.secondarySystemBackground))

                Button {
                    Task { await viewModel.onCreateMealType() }
                    focusedField = nil
                } label: {
                    Text(L10n.MealTypeSheet.buttonCreate)
                        .padding()
                        .frame(maxWidth: .infinity)
                }
                .foregroundStyle(.white)
                .background(.blue)
                .frame(maxWidth: .infinity)
                .padding(.top, -10)
                .font(.system(size: .basic, weight: .bold))
            }
            .cornerRadius(10)
            .overlay(
                RoundedRectangle(cornerRadius: 10)
                    .stroke(.blue, lineWidth: 1)
            )
        }
    }

}

// MARK: - Preview

#Preview {
    MealTypeSheetConfigurator().createView(mealTypes: [
        MealTypeDomain(
            id: "0",
            name: L10n.DefaultMeals.breakfast,
            startTime: Calendar.current.date(bySettingHour: 7, minute: 0, second: 0, of: .now) ?? .now,
            endTime: Calendar.current.date(bySettingHour: 9, minute: 0, second: 0, of: .now) ?? .now
        ),
        MealTypeDomain(
            id: "1",
            name: L10n.DefaultMeals.lunch,
            startTime: Calendar.current.date(bySettingHour: 12, minute: 0, second: 0, of: .now) ?? .now,
            endTime: Calendar.current.date(bySettingHour: 14, minute: 0, second: 0, of: .now) ?? .now
        ),
        MealTypeDomain(
            id: "2",
            name: L10n.DefaultMeals.dinner,
            startTime: Calendar.current.date(bySettingHour: 18, minute: 0, second: 0, of: .now) ?? .now,
            endTime: Calendar.current.date(bySettingHour: 20, minute: 0, second: 0, of: .now) ?? .now
        )
    ])
}
