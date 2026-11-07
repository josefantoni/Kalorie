//
//  ModerationPhotoSuggestionView.swift
//  Kalorie
//
//  Created by Josef Antoni on 07.11.2026.
//

import SwiftUI

struct ModerationPhotoSuggestionView: View {

    // MARK: - Properties

    @StateObject var viewModel: ModerationPhotoSuggestionViewModel
    @Environment(\.dismiss) private var dismiss

    // MARK: - Init

    init(viewModel: ModerationPhotoSuggestionViewModel) {
        self._viewModel = StateObject(wrappedValue: viewModel)
    }

    // MARK: - Body

    var body: some View {
        VStack(spacing: 0) {
            List {
                Section {
                    Color.clear
                        .aspectRatio(1, contentMode: .fit)
                        .overlay { FoodPhotoContent(photo: .remote(viewModel.suggestion.photoURL), contentMode: .fill) }
                        .clipShape(.rect(cornerRadius: 12))
                        .listRowInsets(EdgeInsets())
                    Text(viewModel.itemName ?? viewModel.suggestion.itemId)
                        .fontWeight(.medium)
                }
            }
            .listSectionSpacing(.custom(24))

            VStack(spacing: 12) {
                if viewModel.canApprove {
                    PrimaryButton(L10n.Moderation.buttonApprove) {
                        Task { await viewModel.onApproveTapped() }
                    }
                }
                PrimaryButton(L10n.Moderation.buttonReject, style: .destructive) {
                    Task { await viewModel.onRejectTapped() }
                }
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 12)
            .frame(maxWidth: .infinity)
            .background(Color(uiColor: .systemGroupedBackground))
        }
        .navigationTitle(L10n.Moderation.photoSuggestionTitle)
        .navigationBarTitleDisplayMode(.inline)
        .loader(viewModel.state.isLoading)
        .alert(item: $viewModel.alertItem) { item in
            Alert(
                title: Text(item.title),
                dismissButton: .default(Text(L10n.Common.ok))
            )
        }
        .task { await viewModel.onAppear() }
        .onChange(of: viewModel.shouldDismiss) {
            if viewModel.shouldDismiss { dismiss() }
        }
    }
}

#if DEBUG
// MARK: - Preview

#Preview {
    NavigationStack {
        ModerationPhotoSuggestionView(
            viewModel: ModerationPhotoSuggestionViewModel(
                suggestion: PhotoSuggestionDomain(
                    itemId: "12345678",
                    suggestedBy: "user-1",
                    photoURL: URL(fileURLWithPath: "/tmp/none.jpg"),
                    suggestedAt: .now
                ),
                fetchFoodItemByBarcode: FetchFoodItemByBarcodeUseCaseFake(),
                approvePhotoSuggestion: ApprovePhotoSuggestionUseCaseFake(),
                rejectPhotoSuggestion: RejectPhotoSuggestionUseCaseFake()
            ) {}
        )
    }
}
#endif
