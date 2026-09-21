//
//  MonthCalendarView.swift
//  Kalorie
//
//  Created by Josef Antoni on 30.07.2026.
//

import SwiftUI

struct MonthCalendarView: View {

    // MARK: - Properties

    let selectedDay: Date
    let activeDays: Set<Int>
    let onDaySelected: (Date) -> Void
    let onMonthChanged: (Date) -> Void

    @State private var displayedMonth: Date

    private let calendar = Calendar.current
    private let columns = Array(repeating: GridItem(.flexible(), spacing: 0), count: 7)

    // MARK: - Init

    init(
        selectedDay: Date,
        activeDays: Set<Int>,
        onDaySelected: @escaping (Date) -> Void,
        onMonthChanged: @escaping (Date) -> Void
    ) {
        self.selectedDay = selectedDay
        self.activeDays = activeDays
        self.onDaySelected = onDaySelected
        self.onMonthChanged = onMonthChanged
        self._displayedMonth = State(initialValue: selectedDay)
    }

    // MARK: - Body

    var body: some View {
        VStack(spacing: 12) {
            monthHeader
            weekdayHeader
            daysGrid
        }
        .padding(16)
        .presentationDetents([.medium])
        .presentationDragIndicator(.visible)
        .presentationBackground(.thinMaterial)
    }

    // MARK: - Functions

    private var monthHeader: some View {
        HStack {
            Button {
                changeMonth(by: -1)
            } label: {
                Image(systemName: "chevron.left")
                    .font(.body.weight(.semibold))
            }
            Spacer()
            Text(displayedMonth.formatDateStyle(with: "MMMM yyyy"))
                .font(.headline)
            Spacer()
            Button {
                changeMonth(by: 1)
            } label: {
                Image(systemName: "chevron.right")
                    .font(.body.weight(.semibold))
            }
        }
        .padding(.horizontal, 8)
    }

    private var weekdayHeader: some View {
        LazyVGrid(columns: columns, spacing: 0) {
            ForEach(mondayFirstWeekdaySymbols, id: \.self) { symbol in
                Text(symbol)
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 4)
            }
        }
    }

    private var daysGrid: some View {
        LazyVGrid(columns: columns, spacing: 4) {
            ForEach(0..<leadingEmptyCells, id: \.self) { _ in
                Color.clear.frame(height: 44)
            }
            ForEach(daysInDisplayedMonth, id: \.self) { day in
                CalendarDayCell(
                    day: day,
                    isSelected: isSelected(day: day),
                    isToday: isToday(day: day),
                    hasActivity: activeDays.contains(day)
                ) {
                    selectDay(day)
                }
            }
        }
    }

    private var mondayFirstWeekdaySymbols: [String] {
        let symbols = calendar.shortStandaloneWeekdaySymbols
        return Array(symbols.dropFirst()) + [symbols[0]]
    }

    private var leadingEmptyCells: Int {
        let components = calendar.dateComponents([.year, .month], from: displayedMonth)
        guard let firstOfMonth = calendar.date(from: components) else { return 0 }
        let weekday = calendar.component(.weekday, from: firstOfMonth)
        return (weekday + 5) % 7
    }

    private var daysInDisplayedMonth: [Int] {
        guard let range = calendar.range(of: .day, in: .month, for: displayedMonth) else { return [] }
        return Array(range)
    }

    private func isSelected(day: Int) -> Bool {
        calendar.component(.day, from: selectedDay) == day &&
        calendar.isDate(selectedDay, equalTo: displayedMonth, toGranularity: .month)
    }

    private func isToday(day: Int) -> Bool {
        let today = Date.now
        return calendar.component(.day, from: today) == day &&
        calendar.isDate(today, equalTo: displayedMonth, toGranularity: .month)
    }

    private func selectDay(_ day: Int) {
        var components = calendar.dateComponents([.year, .month], from: displayedMonth)
        components.day = day
        guard let dayOnly = calendar.date(from: components) else { return }

        let timeSource = calendar.isDateInToday(dayOnly) ? Date.now : selectedDay
        let timeComponents = calendar.dateComponents([.hour, .minute, .second], from: timeSource)
        components.hour = timeComponents.hour
        components.minute = timeComponents.minute
        components.second = timeComponents.second

        guard let date = calendar.date(from: components) else { return }
        onDaySelected(date)
    }

    private func changeMonth(by value: Int) {
        guard let newMonth = calendar.date(byAdding: .month, value: value, to: displayedMonth) else { return }
        withAnimation(.spring(response: 0.3, dampingFraction: 0.8)) {
            displayedMonth = newMonth
        }
        onMonthChanged(newMonth)
    }
}

private struct CalendarDayCell: View {

    // MARK: - Properties

    let day: Int
    let isSelected: Bool
    let isToday: Bool
    let hasActivity: Bool
    let onTap: () -> Void

    // MARK: - Body

    var body: some View {
        Button(action: onTap) {
            VStack(spacing: 2) {
                ZStack {
                    if isSelected {
                        Circle()
                            .fill(Color.accentColor)
                            .frame(width: 32, height: 32)
                    } else if isToday {
                        Circle()
                            .stroke(Color.accentColor, lineWidth: 1.5)
                            .frame(width: 32, height: 32)
                    }
                    Text("\(day)")
                        .font(.callout)
                        .foregroundStyle(isSelected ? .white : .primary)
                }
                Circle()
                    .fill(hasActivity ? Color.accentColor.opacity(0.7) : Color.clear)
                    .frame(width: 4, height: 4)
            }
            .frame(height: 44)
        }
        .buttonStyle(.plain)
    }
}
