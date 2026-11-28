//
//  ModerationResponseSheet.swift
//  Kalorie
//
//  Created by Josef Antoni on 14.11.2026.
//

import SwiftUI

struct ModerationResponseDraft: Equatable {

    // MARK: - Properties

    let status: TicketStatus
    var text: String
    var errorTitle: String?

    var isSendable: Bool { !text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }
}

struct ModerationResponseSheet: View {

    // MARK: - Properties

    @Binding var draft: ModerationResponseDraft
    let isSending: Bool
    let onSend: () -> Void

    // MARK: - Body

    var body: some View {
        NavigationStack {
            List {
                Section {
                    TextField(L10n.Moderation.responseTitle, text: $draft.text, axis: .vertical)
                        .lineLimit(4...10)
                }
            }
            .appSectionSpacing()
            .bottomPinnedButtons {
                PrimaryButton(L10n.Moderation.responseSend, action: onSend)
                    .disabled(!draft.isSendable || isSending)
            }
            .navigationTitle(L10n.Moderation.responseTitle)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                DismissToolbarItem()
            }
            .loader(isSending)
            .alert(
                draft.errorTitle ?? "",
                isPresented: Binding(
                    get: { draft.errorTitle != nil },
                    set: { if !$0 { draft.errorTitle = nil } }
                )
            ) {
                Button(L10n.Common.ok, role: .cancel) {}
            }
        }
        .dynamicTypeLimit()
        .interactiveDismissDisabled(isSending)
    }
}

extension View {
    func moderationResponseSheet(
        draft: Binding<ModerationResponseDraft?>,
        isSending: Bool,
        onSend: @escaping () -> Void
    ) -> some View {
        sheet(
            isPresented: Binding(
                get: { draft.wrappedValue != nil },
                set: { if !$0 { draft.wrappedValue = nil } }
            )
        ) {
            ModerationResponseSheet(
                draft: Binding(
                    get: { draft.wrappedValue ?? ModerationResponseDraft(status: .rejected, text: "") },
                    set: { draft.wrappedValue = $0 }
                ),
                isSending: isSending,
                onSend: onSend
            )
        }
    }
}

#if DEBUG

#Preview {
    ModerationResponseSheet(
        draft: .constant(ModerationResponseDraft(status: .accepted, text: "Thanks for the report!")),
        isSending: false
    ) {}
}
#endif
