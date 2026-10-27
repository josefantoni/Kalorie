//
//  BodyWeightHistoryView.swift
//  Kalorie
//
//  Created by Josef Antoni on 27.10.2026.
//

import SwiftUI

struct BodyWeightHistoryView: View {

    // MARK: - Properties

    @StateObject private var viewModel: BodyWeightHistoryViewModel
    @State private var openSwipeRowId: String?

    // MARK: - Init

    init(viewModel: BodyWeightHistoryViewModel) {
        self._viewModel = StateObject(wrappedValue: viewModel)
    }

    // MARK: - Body

    var body: some View {
        ScrollView {
            if viewModel.state.isLoading {
                ProgressView()
                    .padding(.top, 40)
            } else if viewModel.weights.isEmpty {
                emptyState
            } else {
                content
            }
        }
        .background(Color(.systemGroupedBackground))
        .navigationTitle(L10n.WeightHistory.title)
        .navigationBarTitleDisplayMode(.inline)
        .task { await viewModel.onAppear() }
        .onDisappear { viewModel.onDisappear() }
        .simultaneousGesture(TapGesture().onEnded { openSwipeRowId = nil })
        .alert(L10n.WeightHistory.confirmDelete, isPresented: $viewModel.isDeleteConfirmationVisible) {
            Button(L10n.Common.buttonNo, role: .cancel) {}
            Button(L10n.Common.buttonYes, role: .destructive) {
                Task { await viewModel.onDeleteConfirmed() }
            }
        }
        .alert(item: $viewModel.alertItem) { item in
            Alert(title: Text(item.title), message: item.message.map(Text.init), dismissButton: .default(Text(L10n.Common.ok)))
        }
    }

    private var content: some View {
        VStack(spacing: 20) {
            chartSection
            LazyVStack(spacing: 0) {
                ForEach(viewModel.weights, id: \.dayKey) { weight in
                    row(weight)
                    if weight.dayKey != viewModel.weights.last?.dayKey {
                        Divider()
                    }
                }
            }
            .clipShape(RoundedRectangle(cornerRadius: 12))
        }
        .padding()
    }

    private var chartSection: some View {
        VStack(spacing: 12) {
            Picker("", selection: $viewModel.range) {
                Text(L10n.WeightHistory.rangeWeek).tag(BodyWeightRange.week)
                Text(L10n.WeightHistory.rangeMonth).tag(BodyWeightRange.month)
                Text(L10n.WeightHistory.rangeYear).tag(BodyWeightRange.year)
            }
            .pickerStyle(.segmented)

            HStack {
                Button {
                    viewModel.onPreviousPeriod()
                } label: {
                    Image(systemName: BaseImageName.chevronLeft.rawValue)
                }
                .accessibilityLabel(L10n.WeightHistory.previousPeriod)
                Spacer()
                Button {
                    viewModel.onNextPeriod()
                } label: {
                    Image(systemName: BaseImageName.chevronRight.rawValue)
                }
                .disabled(!viewModel.canPageForward)
                .accessibilityLabel(L10n.WeightHistory.nextPeriod)
            }

            BodyWeightChartView(points: viewModel.chartPoints, period: viewModel.period, unit: viewModel.unit)
                .gesture(
                    DragGesture(minimumDistance: 24).onEnded { value in
                        if value.translation.width > 0 {
                            viewModel.onPreviousPeriod()
                        } else {
                            viewModel.onNextPeriod()
                        }
                    }
                )
        }
        .padding()
        .background(Color(.secondarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 12))
    }

    private var emptyState: some View {
        VStack(spacing: 8) {
            Text(L10n.WeightHistory.emptyTitle)
                .font(.headline)
            Text(L10n.WeightHistory.emptyMessage)
                .font(.subheadline)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
        }
        .padding(24)
        .frame(maxWidth: .infinity)
        .background(Color(.secondarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 12))
        .padding()
    }

    // MARK: - Functions

    private func row(_ weight: BodyWeightDomain) -> some View {
        RevealingSwipeToDeleteRow(id: weight.dayKey, openRowId: $openSwipeRowId) {
            viewModel.onDeleteRequested(weight)
        } content: {
            Button {
                viewModel.onEditRequested(weight)
            } label: {
                HStack {
                    Text(dateText(for: weight))
                    Spacer()
                    Text(viewModel.unit.formatted(kilograms: weight.kilograms))
                        .font(.body.bold())
                }
                .foregroundStyle(Color(.label))
                .padding(.horizontal, 16)
                .padding(.vertical, 15)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .allowsHitTesting(openSwipeRowId == nil)
            .popover(isPresented: editBinding(for: weight)) {
                editPopover
            }
        }
    }

    private func dateText(for weight: BodyWeightDomain) -> String {
        BodyWeightDayKey.date(from: weight.dayKey)?.formatted(date: .abbreviated, time: .omitted) ?? weight.dayKey
    }

    private func editBinding(for weight: BodyWeightDomain) -> Binding<Bool> {
        Binding(
            get: { viewModel.editingDayKey == weight.dayKey },
            set: { if !$0 { viewModel.editingDayKey = nil } }
        )
    }

    private var editPopover: some View {
        VStack(spacing: 12) {
            WeightPickerView(kilograms: $viewModel.editingKilograms, unit: viewModel.unit)
            Button {
                Task { await viewModel.onEditConfirmed() }
            } label: {
                if viewModel.showCheckmark {
                    Image(systemName: BaseImageName.checkmark.rawValue)
                        .transition(.scale.combined(with: .opacity))
                } else {
                    Text(L10n.Common.buttonDone)
                        .transition(.opacity)
                }
            }
            .buttonStyle(.glassProminent)
            .tint(viewModel.showCheckmark ? Color.success : Color.accentColor)
            .animation(.spring(duration: 0.4), value: viewModel.showCheckmark)
            .disabled(viewModel.isSaving || viewModel.showCheckmark)
        }
        .padding()
        .frame(width: 280)
        .interactiveDismissDisabled(viewModel.isSaving || viewModel.showCheckmark)
        .dynamicTypeLimit()
        .presentationCompactAdaptation(.popover)
    }
}
