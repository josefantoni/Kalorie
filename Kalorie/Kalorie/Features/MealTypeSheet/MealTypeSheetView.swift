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

    @ObservedObject var viewModel: MealTypeSheetViewModel
    @FocusState private var focusedField: Field?
    @Environment(\.dismiss) var dismiss

    private enum Field: Int, CaseIterable {
        case newMealName
    }

    // MARK: - Init

    init(viewModel: MealTypeSheetViewModel) {
        self.viewModel = viewModel
    }

    // MARK: - Body

    var body: some View {
        NavigationView {
            VStack(spacing: 0) {
                List {
                    Section(
                        header: Text(L10n.MealTypeSheet.sectionMealLayout),
                        footer: footerView
                            .padding([.leading, .trailing], -20)
                            .padding(.top, 20)
                    ) {
                        ForEach($viewModel.mealTypes, id: \.id, editActions: .move) { mealType in
                            MealTypeItemView(mealType.wrappedValue)
                        }
                        .onDelete { indexSet in
                            if let index = indexSet.first {
                                Task { await viewModel.onDelete(at: index) }
                            }
                        }
                    }
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topTrailing)
            .toolbar {
                dismissButton
            }
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
                        .padding([.leading, .trailing], 20)
                        .font(.system(size: .basic))
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
                    .padding([.leading, .trailing], 20)
                    .padding(.top, 10)
                    .font(.system(size: .basic))
                }
                .padding(.bottom, 20)
                .background(.white)

                Button {
                    Task { await viewModel.onCreateMealType() }
                    focusedField = nil
                } label: {
                    Text(L10n.MealTypeSheet.buttonCreate)
                        .padding()
                        .frame(maxWidth: .infinity)
                }
                .foregroundColor(.white)
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

    var dismissButton: ToolbarItem<(), some View> {
        ToolbarItem(placement: .topBarLeading) {
            BaseButton(
                style: .plain,
                imageName: .close,
                imageSize: .basic
            ) {
                dismiss()
            }
        }
    }
}

// MARK: - Preview

#Preview {
    MealTypeSheetConfigurator().createView(mealTypes: [])
}
