//
//  MealSectionCopyView.swift
//  Kalorie
//
//  Created by Josef Antoni on 28.09.2026.
//

import SwiftUI

struct MealSectionCopyView: View {

    // MARK: - Properties

    @ObservedObject var viewModel: DashboardViewModel
    let name: String
    let mealType: MealTypeDomain?
    let foods: [FoodConsumedDomain]

    private let textColor = Color(uiColor: .label)

    // MARK: - Body

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(L10n.Dashboard.copyMessage(name: name))
                .font(.headline)
                .foregroundStyle(textColor)
                .fixedSize(horizontal: false, vertical: true)
            Divider()

            HStack {
                Text(L10n.Dashboard.copyDay)
                    .foregroundStyle(textColor)
                Spacer()
                DatePicker(
                    L10n.Dashboard.copyDay,
                    selection: $viewModel.copyTargetDay,
                    in: ...Date.now,
                    displayedComponents: .date
                )
                .datePickerStyle(.compact)
                .labelsHidden()
            }

            HStack {
                Text(L10n.Dashboard.copyMeal)
                    .foregroundStyle(textColor)
                Spacer()
                Picker(L10n.Dashboard.copyMeal, selection: $viewModel.copyTargetMealTypeId) {
                    ForEach(viewModel.mealTypes.sorted { $0.startMinutes < $1.startMinutes }, id: \.id) { mealType in
                        Text(mealType.name).tag(Optional(mealType.id))
                    }
                }
                .pickerStyle(.menu)
                .labelsHidden()
                .tint(.primary)
            }

            Divider()
            HStack {
                Button(L10n.Common.buttonCancel, role: .cancel) {
                    viewModel.copyPopoverIndex = nil
                }
                .disabled(viewModel.isCopying || viewModel.showCopyCheckmark)
                Spacer()
                Button {
                    Task { await viewModel.onCopyConfirmed(foods, from: mealType) }
                } label: {
                    if viewModel.showCopyCheckmark {
                        Image(systemName: BaseImageName.checkmark.rawValue)
                            .transition(.scale.combined(with: .opacity))
                    } else {
                        Text(L10n.Dashboard.copyButton)
                            .transition(.opacity)
                    }
                }
                .buttonStyle(.glassProminent)
                .tint(viewModel.showCopyCheckmark ? Color.success : .accentColor)
                .animation(.spring(duration: 0.4), value: viewModel.showCopyCheckmark)
                .disabled(!viewModel.canCopy(from: mealType) && !viewModel.showCopyCheckmark)
            }
        }
        .padding()
        .frame(minWidth: 260, idealWidth: 300, maxWidth: 340)
        .interactiveDismissDisabled(viewModel.isCopying || viewModel.showCopyCheckmark)
    }
}
