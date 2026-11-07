//
//  ModerationPhotoSuggestionsView.swift
//  Kalorie
//
//  Created by Josef Antoni on 07.11.2026.
//

import SwiftUI

struct ModerationPhotoSuggestionsView: View {

    // MARK: - Properties

    @StateObject var viewModel: ModerationPhotoSuggestionsViewModel
    private let makeReviewView: (PhotoSuggestionDomain, @escaping (Bool) -> Void) -> ModerationPhotoSuggestionView

    // MARK: - Init

    init(
        viewModel: ModerationPhotoSuggestionsViewModel,
        makeReviewView: @escaping (PhotoSuggestionDomain, @escaping (Bool) -> Void) -> ModerationPhotoSuggestionView
    ) {
        self._viewModel = StateObject(wrappedValue: viewModel)
        self.makeReviewView = makeReviewView
    }

    // MARK: - Body

    var body: some View {
        List {
            if viewModel.rows.isEmpty && !viewModel.state.isLoading {
                Text(L10n.Moderation.photosEmpty)
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            }
            ForEach(viewModel.rows) { row in
                NavigationLink {
                    makeReviewView(row.suggestion) { isApproved in
                        viewModel.onSuggestionResolved(id: row.id, isApproved: isApproved)
                    }
                } label: {
                    Text(row.itemName ?? row.suggestion.itemId)
                }
            }
        }
        .navigationTitle(L10n.Moderation.photosTitle)
        .navigationBarTitleDisplayMode(.inline)
        .loader(viewModel.state.isLoading)
        .alert(item: $viewModel.alertItem) { item in
            Alert(
                title: Text(item.title),
                dismissButton: .default(Text(L10n.Common.ok))
            )
        }
        .task { await viewModel.onAppear() }
        .refreshable { await viewModel.onRefresh() }
    }
}
