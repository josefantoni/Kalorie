//
//  GoalInfoView.swift
//  Kalorie
//
//  Created by Josef Antoni on 06.11.2026.
//

import SwiftUI

struct GoalInfoView: View {

    // MARK: - Properties

    let goal: DailyGoalDomain?
    let eatenKcal: Int
    let onOpenGoals: () -> Void

    // MARK: - Body

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            if let kcalGoal = goal?.kcalGoal {
                let difference = Int(kcalGoal.rounded()) - eatenKcal
                row(L10n.Goals.infoEaten, kcal(eatenKcal))
                row(L10n.Goals.infoGoal, kcal(Int(kcalGoal.rounded())))
                row(difference >= 0 ? L10n.Goals.infoRemaining : L10n.Goals.infoOver, kcal(abs(difference)))
            } else {
                Text(L10n.Goals.infoNoGoalMessage)
                    .font(.subheadline)
                    .foregroundStyle(Color(.label))
                    .fixedSize(horizontal: false, vertical: true)
            }
            Text(L10n.Goals.infoDonutNote)
                .font(.caption)
                .foregroundStyle(Color(.label))
                .fixedSize(horizontal: false, vertical: true)
            Button {
                onOpenGoals()
            } label: {
                Text(goal == nil ? L10n.Goals.infoButtonSet : L10n.Goals.infoButtonEdit)
                    .frame(maxWidth: .infinity)
                    .frame(height: 28)
            }
            .buttonStyle(.glassProminent)
            .buttonBorderShape(.capsule)
            .tint(Color.accentColor)
        }
    }

    // MARK: - Functions

    private func kcal(_ value: Int) -> String {
        "\(value.formatted()) \(L10n.Common.unitKcal)"
    }

    private func row(_ title: String, _ value: String) -> some View {
        HStack {
            Text(title)
            Spacer()
            Text(value)
                .bold()
        }
        .font(.subheadline)
        .foregroundStyle(Color(.label))
    }
}

#if DEBUG
// MARK: - Preview

#Preview {
    GoalInfoView(goal: nil, eatenKcal: 1200) {}
        .padding()
}
#endif
