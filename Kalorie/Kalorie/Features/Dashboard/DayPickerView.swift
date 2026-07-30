//
//  DayPickerView.swift
//  Kalorie
//
//  Created by Josef Antoni on 30.07.2026.
//

import SwiftUI

struct DayPickerView: View {

    // MARK: - Properties

    @Binding var selectedDay: Date
    let activeDays: Set<Int>
    let onDayChanged: (Date) -> Void
    let onTapSelectedDay: () -> Void

    private let calendar = Calendar.current

    // MARK: - Body

    var body: some View {
        HStack(spacing: 0) {
            dayCard(for: adjacentDay(-1), isSelected: false)
                .contentShape(Rectangle())
                .onTapGesture {
                    withAnimation(.spring(response: 0.3, dampingFraction: 0.8)) {
                        navigate(by: -1)
                    }
                }
            dayCard(for: selectedDay, isSelected: true)
                .contentShape(Rectangle())
                .onTapGesture { onTapSelectedDay() }
            dayCard(for: adjacentDay(1), isSelected: false)
                .contentShape(Rectangle())
                .onTapGesture {
                    withAnimation(.spring(response: 0.3, dampingFraction: 0.8)) {
                        navigate(by: 1)
                    }
                }
        }
        .padding(.vertical, 8)
        .glassEffect(.regular, in: .rect(cornerRadius: 16))
        .gesture(
            DragGesture(minimumDistance: 15)
                .onEnded { value in
                    if value.translation.width < -30 {
                        withAnimation(.spring(response: 0.3, dampingFraction: 0.8)) {
                            navigate(by: 1)
                        }
                    } else if value.translation.width > 30 {
                        withAnimation(.spring(response: 0.3, dampingFraction: 0.8)) {
                            navigate(by: -1)
                        }
                    }
                }
        )
    }

    // MARK: - Functions

    @ViewBuilder
    private func dayCard(for date: Date, isSelected: Bool) -> some View {
        VStack(spacing: 4) {
            Text(date.formatDateStyle(with: "EEE").uppercased())
                .font(.caption2)
                .foregroundStyle(isSelected ? .primary : .secondary)

            ZStack {
                if isSelected {
                    Circle()
                        .fill(Color.accentColor)
                        .frame(width: 36, height: 36)
                }
                Text("\(calendar.component(.day, from: date))")
                    .font(isSelected ? .headline : .body)
                    .foregroundStyle(isSelected ? .white : .primary)
            }

            Circle()
                .fill(hasActivity(for: date) ? Color.accentColor.opacity(0.6) : Color.clear)
                .frame(width: 4, height: 4)
        }
        .frame(maxWidth: .infinity)
    }

    private func hasActivity(for date: Date) -> Bool {
        guard calendar.isDate(date, equalTo: selectedDay, toGranularity: .month) else { return false }
        return activeDays.contains(calendar.component(.day, from: date))
    }

    private func adjacentDay(_ offset: Int) -> Date {
        calendar.date(byAdding: .day, value: offset, to: selectedDay) ?? selectedDay
    }

    private func navigate(by offset: Int) {
        guard let newDay = calendar.date(byAdding: .day, value: offset, to: selectedDay) else { return }
        selectedDay = newDay
        onDayChanged(newDay)
    }
}
