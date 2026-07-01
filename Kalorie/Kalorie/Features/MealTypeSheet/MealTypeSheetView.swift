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
    @FocusState private var focusedField: Field?
    @State private var editMode: EditMode = .inactive

    private enum Field: Int, CaseIterable {
        case newMealName
    }

    // MARK: - Init

    init(viewModel: MealTypeSheetViewModel) {
        _viewModel = StateObject(wrappedValue: viewModel)
    }

    // MARK: - Body

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                List {
                    Section(
                        header: Text(L10n.MealTypeSheet.sectionMealLayout),
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
                        .onDelete { indexSet in
                            if let index = indexSet.first {
                                Task { await viewModel.onDelete(at: index) }
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
                    ToolbarItem(placement: .topBarTrailing) {
                        Button {
                            withAnimation { editMode = .active }
                        } label: {
                            Image(systemName: "pencil")
                        }
                    }
                } else {
                    ToolbarItem(placement: .topBarTrailing) {
                        Button {
                            withAnimation { editMode = .inactive }
                            Task { await viewModel.onSaveReorder() }
                        } label: {
                            Image(systemName: "checkmark")
                        }
                    }
                }
            }
            .loader(viewModel.state.isLoading)
            .interactiveDismissDisabled(editMode == .active)
            .alert(isPresented: $viewModel.showingAlert) {
                Alert(
                    title: Text(viewModel.alertTitle),
                    dismissButton: Alert.Button.default(Text(L10n.Common.ok))
                )
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
            id: 0,
            name: L10n.DefaultMeals.breakfast,
            startTime: Calendar.current.date(bySettingHour: 7, minute: 0, second: 0, of: .now) ?? .now,
            endTime: Calendar.current.date(bySettingHour: 9, minute: 0, second: 0, of: .now) ?? .now
        ),
        MealTypeDomain(
            id: 1,
            name: L10n.DefaultMeals.lunch,
            startTime: Calendar.current.date(bySettingHour: 12, minute: 0, second: 0, of: .now) ?? .now,
            endTime: Calendar.current.date(bySettingHour: 14, minute: 0, second: 0, of: .now) ?? .now
        ),
        MealTypeDomain(
            id: 2,
            name: L10n.DefaultMeals.dinner,
            startTime: Calendar.current.date(bySettingHour: 18, minute: 0, second: 0, of: .now) ?? .now,
            endTime: Calendar.current.date(bySettingHour: 20, minute: 0, second: 0, of: .now) ?? .now
        )
    ])
}
