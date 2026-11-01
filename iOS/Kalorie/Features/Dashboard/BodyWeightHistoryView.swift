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
                LoaderIndicator()
                    .frame(maxWidth: .infinity)
                    .padding(.top, 40)
            } else if case .error = viewModel.state {
                loadFailedState
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
            .onChange(of: viewModel.range) {
                Task { await viewModel.loadEarlierYearsIfNeeded() }
            }

            HStack {
                Button {
                    Task { await viewModel.onPreviousPeriod() }
                } label: {
                    Image(systemName: BaseImageName.chevronLeft.rawValue)
                }
                .disabled(!viewModel.canPageBack)
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
                .opacity(viewModel.isLoadingEarlierYear ? 0.3 : 1)
                .overlay {
                    if viewModel.isLoadingEarlierYear {
                        LoaderIndicator()
                    }
                }
                .gesture(
                    DragGesture(minimumDistance: 24).onEnded { value in
                        guard abs(value.translation.width) > abs(value.translation.height) else { return }
                        if value.translation.width > 0 {
                            Task { await viewModel.onPreviousPeriod() }
                        } else {
                            viewModel.onNextPeriod()
                        }
                    }
                )
        }
        .padding()
        .background(Color(.secondarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 12))
    }

    private var loadFailedState: some View {
        VStack(spacing: 12) {
            Text(L10n.WeightHistory.errorLoadFailed)
                .font(.headline)
                .multilineTextAlignment(.center)
            Button(L10n.Auth.buttonRetry) {
                Task { await viewModel.onRetryTapped() }
            }
            .buttonStyle(.glass)
        }
        .padding(24)
        .frame(maxWidth: .infinity)
        .background(Color(.secondarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 12))
        .padding()
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
            HStack {
                Text(dateText(for: weight))
                Spacer()
                Text(viewModel.unit.formatted(kilograms: weight.kilograms))
                    .font(.body.bold())
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 15)
            .contentShape(Rectangle())
        }
    }

    private func dateText(for weight: BodyWeightDomain) -> String {
        BodyWeightDayKey.date(from: weight.dayKey)?.formatted(date: .abbreviated, time: .omitted) ?? weight.dayKey
    }
}
