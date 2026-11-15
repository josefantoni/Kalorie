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
                            if editMode == .active && !viewModel.isAddFormVisible {
                                BadgeButton(imageName: .plus) {
                                    viewModel.onShowAddForm()
                                }
                                .frame(maxWidth: .infinity)
                                .padding(.top, 12)
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
                                        Image(systemName: BaseImageName.trash.rawValue)
                                    }
                                }
                        }
                        .onMove { from, to in
                            viewModel.onMove(from: from, to: to)
                        }
                    }

                    if editMode == .active && viewModel.isAddFormVisible {
                        newMealFormSection
                    }

                    if editMode == .inactive {
                        if viewModel.hasLoadedAppSettings {
                            Section(header: Text(L10n.Settings.sectionBodyWeight)) {
                                Toggle(
                                    L10n.Settings.bodyWeightShow,
                                    isOn: Binding(
                                        get: { viewModel.appSettings.showBodyWeight },
                                        set: { isShown in Task { await viewModel.onShowBodyWeightChanged(isShown) } }
                                    )
                                )
                                .tint(.accentColor)
                                Picker(
                                    L10n.Settings.bodyWeightUnit,
                                    selection: Binding(
                                        get: { viewModel.appSettings.bodyWeightUnit },
                                        set: { unit in Task { await viewModel.onBodyWeightUnitChanged(unit) } }
                                    )
                                ) {
                                    ForEach(BodyWeightUnit.allCases, id: \.self) { unit in
                                        Text(unit.symbol).tag(unit)
                                    }
                                }
                                .pickerStyle(.segmented)
                            }

                            Section(header: Text(L10n.Settings.sectionDailyGoal)) {
                                Toggle(
                                    L10n.Settings.dailyGoalShow,
                                    isOn: Binding(
                                        get: { viewModel.appSettings.showDailyGoal },
                                        set: { isShown in Task { await viewModel.onShowDailyGoalChanged(isShown) } }
                                    )
                                )
                                .tint(.accentColor)
                                Toggle(
                                    L10n.Settings.goalBarsShow,
                                    isOn: Binding(
                                        get: { viewModel.appSettings.showGoalBars },
                                        set: { isShown in Task { await viewModel.onShowGoalBarsChanged(isShown) } }
                                    )
                                )
                                .tint(.accentColor)
                                .disabled(!viewModel.appSettings.showDailyGoal)
                                Toggle(
                                    L10n.Settings.goalPercentShow,
                                    isOn: Binding(
                                        get: { viewModel.appSettings.showGoalPercent },
                                        set: { isShown in Task { await viewModel.onShowGoalPercentChanged(isShown) } }
                                    )
                                )
                                .tint(.accentColor)
                                .disabled(!viewModel.appSettings.showDailyGoal)
                            }
                        }

                        Section(header: Text(L10n.Settings.sectionOther)) {
                            Button {
                                viewModel.isExportPushed = true
                            } label: {
                                HStack {
                                    Text(L10n.Export.navigationTitle)
                                    Spacer()
                                    Image(systemName: BaseImageName.chevronRight.rawValue)
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
                                        Image(systemName: BaseImageName.chevronRight.rawValue)
                                            .font(.footnote.weight(.semibold))
                                            .foregroundStyle(.tertiary)
                                    }
                                    .contentShape(Rectangle())
                                }
                                .buttonStyle(.plain)
                            }
                        }

                        if Constants.Tips.isEnabled {
                            Section {
                                Button {
                                    viewModel.isSupportPushed = true
                                } label: {
                                    HStack(spacing: 12) {
                                        Image(systemName: BaseImageName.heartFill.rawValue)
                                            .font(.body.weight(.semibold))
                                            .foregroundStyle(.white)
                                            .frame(width: Self.supportIconSize, height: Self.supportIconSize)
                                            .background(Circle().fill(Color.favourite))
                                        VStack(alignment: .leading, spacing: 2) {
                                            Text(L10n.Settings.buttonSupport)
                                                .fontWeight(.semibold)
                                            Text(L10n.Settings.supportSubtitle)
                                                .font(.footnote)
                                                .foregroundStyle(.secondary)
                                        }
                                        Spacer()
                                        Image(systemName: BaseImageName.chevronRight.rawValue)
                                            .font(.footnote.weight(.semibold))
                                            .foregroundStyle(.tertiary)
                                    }
                                    .padding(.vertical, 6)
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
                                NavigationLink {
                                    router.makeModerationPhotoSuggestionsView()
                                } label: {
                                    Text(L10n.Moderation.photosTitle)
                                }
                            }
                        }
                    }
                }
                .environment(\.editMode, $editMode)
                .scrollRowIntoView(Self.newMealTimesRowID, when: focusedField != nil)
                .bottomPinnedButtons(isVisible: editMode == .active && viewModel.isAddFormVisible) {
                    PrimaryButton(L10n.Settings.buttonCreate) {
                        Task { await viewModel.onCreateMealType() }
                        focusedField = nil
                    }
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topTrailing)
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
            .navigationDestination(isPresented: $viewModel.isSupportPushed) {
                router.makeSupportView()
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

    private var newMealFormSection: some View {
        Section {
            FloatingLabelTextField(
                title: L10n.Settings.fieldNewMealTitle,
                text: $viewModel.newMealName,
                focus: $focusedField,
                equals: .newMealName
            )
            .frame(minHeight: Self.newMealRowHeight)
            .listRowInsets(.vertical, 0)

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
            .frame(minHeight: Self.newMealRowHeight)
            .listRowInsets(.vertical, 0)
            .id(Self.newMealTimesRowID)
        }
        .listSectionSpacing(.custom(20))
    }

    private static var newMealTimesRowID: String { "newMealTimes" }
    private static var newMealRowHeight: CGFloat { 56 }
    private static var supportIconSize: CGFloat { 36 }

}

private extension View {
    func scrollRowIntoView(_ id: String, when isTriggered: Bool) -> some View {
        modifier(ScrollRowIntoView(id: id, isTriggered: isTriggered))
    }
}

private struct ScrollRowIntoView: ViewModifier {

    // MARK: - Properties

    let id: String
    let isTriggered: Bool

    // MARK: - Body

    func body(content: Content) -> some View {
        ScrollViewReader { proxy in
            content
                .onChange(of: isTriggered) {
                    guard isTriggered else { return }
                    Task {
                        // Wait for the keyboard's own scroll-to-focus to finish, otherwise it overrides ours.
                        try? await Task.sleep(for: .milliseconds(400))
                        withAnimation { proxy.scrollTo(id, anchor: .bottom) }
                    }
                }
        }
    }
}

#if DEBUG
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
#endif
