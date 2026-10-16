//
//  SupportView.swift
//  Kalorie
//
//  Created by Josef Antoni on 15.10.2026.
//

import SwiftUI

struct SupportView: View {

    // MARK: - Properties

    @StateObject var viewModel: SupportViewModel

    // MARK: - Init

    init(viewModel: SupportViewModel) {
        _viewModel = StateObject(wrappedValue: viewModel)
    }

    // MARK: - Body

    var body: some View {
        List {
            Section {
                Text(L10n.Support.intro)
                    .foregroundStyle(.secondary)
            }
            Section {
                ForEach(viewModel.products) { product in
                    Button {
                        Task { await viewModel.onProductTapped(product) }
                    } label: {
                        HStack {
                            Text(L10n.Support.productTitle(product.id))
                            Spacer()
                            Text(product.displayPrice)
                                .foregroundStyle(.secondary)
                        }
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                }
            }
        }
        .navigationTitle(L10n.Settings.buttonSupport)
        .navigationBarTitleDisplayMode(.inline)
        .task { await viewModel.onAppear() }
        .loader(viewModel.state != .idle)
        .overlay {
            if viewModel.isThankYouVisible {
                thankYouOverlay
                    .transition(.opacity)
            }
        }
        .animation(.easeInOut(duration: 0.8), value: viewModel.isThankYouVisible)
        .alert(item: $viewModel.alertItem) { item in
            Alert(
                title: Text(item.title),
                message: item.message.map(Text.init),
                dismissButton: Alert.Button.default(Text(L10n.Common.ok))
            )
        }
    }
}

extension SupportView {

    // MARK: - Private

    @ViewBuilder fileprivate var thankYouOverlay: some View {
        switch viewModel.thankYouAnimation {
        case .baguettes:
            ZStack {
                BaguetteRainView(duration: 5, count: 40)
                VStack(spacing: 24) {
                    thankYouMessage(L10n.Support.thankYouMessageSmall)
                    dismissButton
                }
                .padding(.horizontal, 32)
                .padding(.vertical, 24)
                .background(.regularMaterial, in: .rect(cornerRadius: 24))
                .padding(.horizontal, 24)
            }
        case .lunch:
            illustratedThankYou(message: L10n.Support.thankYouMessageMedium) { LunchIllustrationView() }
        case .grandma:
            illustratedThankYou(message: L10n.Support.thankYouMessageLarge) { GrandmaCookingView() }
        }
    }

    private func thankYouMessage(_ message: String) -> some View {
        Text(message)
            .font(.title3.weight(.semibold))
            .multilineTextAlignment(.center)
    }

    private var dismissButton: some View {
        PrimaryButton(L10n.Support.thankYouDismiss) {
            viewModel.onThankYouDismissed()
        }
    }

    private func illustratedThankYou<Illustration: View>(
        message: String,
        @ViewBuilder illustration: () -> Illustration
    ) -> some View {
        ZStack {
            Color(.systemBackground)
                .opacity(0.92)
                .ignoresSafeArea()
            VStack(spacing: 24) {
                illustration()
                thankYouMessage(message)
                dismissButton
            }
            .padding(.horizontal, 24)
        }
    }
}

#if DEBUG
// MARK: - Preview

#Preview {
    NavigationStack {
        SupportView(
            viewModel: SupportViewModel(
                loadTipProducts: LoadTipProductsUseCaseFake(
                    stubbedProducts: [
                        TipProductDomain(id: "tip_small", displayPrice: "29 Kč"),
                        TipProductDomain(id: "tip_medium", displayPrice: "79 Kč"),
                        TipProductDomain(id: "tip_large", displayPrice: "199 Kč")
                    ]
                ),
                purchaseTip: PurchaseTipUseCaseFake()
            )
        )
    }
}
#endif
