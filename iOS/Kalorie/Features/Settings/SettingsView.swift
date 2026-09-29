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
    @Environment(\.openURL) private var openURL
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

                            if let feedbackURL = viewModel.feedbackURL {
                                Button {
                                    openURL(feedbackURL) { isAccepted in
                                        if !isAccepted {
                                            viewModel.isFeedbackMailUnavailableAlertPresented = true
                                        }
                                    }
                                } label: {
                                    HStack {
                                        Text(L10n.Settings.buttonFeedback)
                                        Spacer()
                                        Image(systemName: "chevron.right")
                                            .font(.footnote.weight(.semibold))
                                            .foregroundStyle(.tertiary)
                                    }
                                    .contentShape(Rectangle())
                                }
                                .buttonStyle(.plain)
                            }
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
            .alert(
                L10n.Settings.feedbackNoMailTitle,
                isPresented: $viewModel.isFeedbackMailUnavailableAlertPresented
            ) {
                Button(L10n.Settings.feedbackNoMailCopy) {
                    UIPasteboard.general.string = Constants.Support.email
                }
                Button(L10n.Common.ok, role: .cancel) {}
            } message: {
                Text(L10n.Settings.feedbackNoMailMessage(Constants.Support.email))
            }
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
            VStack {
                VStack {
                    FloatingLabelTextField(
                        title: L10n.Settings.fieldNewMealTitle,
                        text: $viewModel.newMealName,
                        focus: $focusedField,
                        equals: .newMealName
                    )
                    .padding(.horizontal, 16)
                    .padding(.top, 8)
                    .padding(.bottom, 0)
                    Divider()
                        .padding(.horizontal)

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
                    .padding(.horizontal, 16)
                    .font(.system(size: .basic))
                }
                .padding(.bottom, 8)
                .background(Color(.systemBackground))
                .cornerRadius(25)

                Button {
                    Task { await viewModel.onCreateMealType() }
                    focusedField = nil
                } label: {
                    Text(L10n.Settings.buttonCreate)
                        .frame(maxWidth: .infinity)
                        .fontWeight(.bold)
                }
                .buttonStyle(.glassProminent)
                .controlSize(.large)
                .padding(.top, 20)
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
