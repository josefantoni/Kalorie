//
//  SettingsView.swift
//  Kalorie
//
//  Created by Josef Antoni on 08.06.2024.
//

import Foundation
import SwiftUI

struct SettingsView: View {

    // MARK: - Properties

    @StateObject var viewModel: SettingsViewModel
    private let router: SettingsRouter
    @FocusState private var focusedField: Field?
    @State private var editMode: EditMode = .inactive

    private enum Field: Int, CaseIterable {
        case newMealName
    }

    // MARK: - Init

    init(router: SettingsRouter, viewModel: SettingsViewModel) {
        self.router = router
        _viewModel = StateObject(wrappedValue: viewModel)
    }

    // MARK: - Body

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                List {
                    Section(
                        header: HStack {
                            Text(L10n.Settings.sectionMealLayout)
                            Spacer()
                            Button {
                                if editMode == .active {
                                    withAnimation { editMode = .inactive }
                                    Task { await viewModel.onSaveReorder() }
                                } else {
                                    withAnimation { editMode = .active }
                                }
                            } label: {
                                Text(editMode == .active ? L10n.Settings.buttonEditDone : L10n.Settings.buttonEdit)
                            }
                            .buttonStyle(.glass)
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

                    if editMode == .inactive {
                        Section(header: Text(L10n.Settings.sectionOther)) {
                            Button {
                                viewModel.isExportPushed = true
                            } label: {
                                HStack {
                                    Text(L10n.Export.navigationTitle)
                                    Spacer()
                                    Image(systemName: "chevron.right")
                                        .font(.footnote.weight(.semibold))
                                        .foregroundStyle(.tertiary)
                                }
                                .contentShape(Rectangle())
                            }
                            .buttonStyle(.plain)
                        }

                        if viewModel.isMaintainer {
                            Section(header: Text(L10n.Moderation.sectionTitle)) {
                                NavigationLink {
                                    router.makeModerationQueueView()
                                } label: {
                                    Text(L10n.Moderation.queueTitle)
                                }
                                NavigationLink {
                                    router.makeModerationReportsView()
                                } label: {
                                    Text(L10n.Moderation.reportsTitle)
                                }
                            }
                        }
                    }
                }
                .environment(\.editMode, $editMode)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topTrailing)
            .keyboardDoneToolbar()
            .task { await viewModel.onAppear() }
            .navigationTitle(L10n.Settings.navigationTitle)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                if editMode == .inactive {
                    DismissToolbarItem()
                }
            }
            .navigationDestination(isPresented: $viewModel.isExportPushed) {
                router.makeExportView(mealTypes: viewModel.mealTypes)
            }
            .loader(viewModel.state.isLoading)
            .interactiveDismissDisabled(editMode == .active)
            .alert(item: $viewModel.alertItem) { item in
                Alert(
                    title: Text(item.title),
                    message: item.message.map(Text.init),
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
            VStack(spacing: 12) {
                VStack {
                    TextField(L10n.Settings.fieldNewMealPlaceholder, text: $viewModel.newMealName)
                        .padding(.horizontal, 20)
                        .font(.system(size: .smallPlus))
                        .padding(.top, 20)
                        .padding(.bottom, 10)
                        .focused($focusedField, equals: .newMealName)
                    Divider()

                    HStack {
                        DatePicker(L10n.Settings.datePickerFrom, selection: $viewModel.newMealStart, displayedComponents: .hourAndMinute)
                            .datePickerStyle(GraphicalDatePickerStyle())
                            .onChange(of: viewModel.newMealStart) {
                                if viewModel.newMealStart >= viewModel.newMealEnd {
                                    viewModel.newMealEnd = viewModel.newMealStart.withAddedMinutes(minutes: 30)
                                }
                            }
                        Divider()
                        DatePicker(L10n.Settings.datePickerTo, selection: $viewModel.newMealEnd, displayedComponents: .hourAndMinute)
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
                .cornerRadius(10)
                .overlay(
                    RoundedRectangle(cornerRadius: 10)
                        .stroke(Color.accentColor, lineWidth: 1)
                )

                Button {
                    Task { await viewModel.onCreateMealType() }
                    focusedField = nil
                } label: {
                    Text(L10n.Settings.buttonCreate)
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(.glassProminent)
                .controlSize(.large)
            }
        }
    }

}

// MARK: - Preview

#Preview {
    SettingsConfigurator().createView(mealTypes: [
        MealTypeDomain(
            id: "0",
            name: L10n.DefaultMeals.breakfast,
            startMinutes: 7 * 60,
            endMinutes: 9 * 60
        ),
        MealTypeDomain(
            id: "1",
            name: L10n.DefaultMeals.lunch,
            startMinutes: 12 * 60,
            endMinutes: 14 * 60
        ),
        MealTypeDomain(
            id: "2",
            name: L10n.DefaultMeals.dinner,
            startMinutes: 18 * 60,
            endMinutes: 20 * 60
        )
    ])
}
