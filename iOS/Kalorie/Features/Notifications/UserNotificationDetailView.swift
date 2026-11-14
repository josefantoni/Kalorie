//
//  UserNotificationDetailView.swift
//  Kalorie
//
//  Created by Josef Antoni on 14.11.2026.
//

import SwiftUI

struct UserNotificationDetailView: View {

    // MARK: - Properties

    @Environment(\.dismiss) private var dismiss
    @State private var isWithdrawConfirmationVisible = false
    @State private var isWithdrawErrorVisible = false
    @State private var isWithdrawing = false

    let title: String
    let notification: UserNotificationDomain
    let onShown: () async -> Void
    let onWithdraw: () async -> NotificationsViewModel.WithdrawResult

    // MARK: - Body

    var body: some View {
        List {
            Section {
                if let photoURL = notification.photoURL {
                    Color.clear
                        .aspectRatio(1, contentMode: .fit)
                        .overlay { FoodPhotoContent(photo: .remote(photoURL), contentMode: .fill) }
                        .clipShape(.rect(cornerRadius: 12))
                        .listRowInsets(EdgeInsets())
                }
                HStack {
                    Text(L10n.Notifications.userStatusTitle)
                        .foregroundStyle(.secondary)
                    Spacer()
                    Text(notification.statusLabel)
                        .fontWeight(.medium)
                        .foregroundStyle(statusColor)
                }
                if notification.status == .pending {
                    Text(notification.kind.message)
                }
                Text(L10n.Notifications.userSentAt(notification.createdAt.formatted(date: .abbreviated, time: .shortened)))
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            }
            if let reason = notification.reason {
                Section(L10n.Notifications.userReasonTitle) {
                    Text(reason)
                }
            }
            if notification.status != .pending, let response = notification.response {
                Section(L10n.Notifications.userResponseTitle) {
                    Text(response)
                    if let resolvedAt = notification.resolvedAt {
                        Text(L10n.Notifications.userResolvedAt(resolvedAt.formatted(date: .abbreviated, time: .shortened)))
                            .font(.footnote)
                            .foregroundStyle(.secondary)
                    }
                }
            }
        }
        .bottomPinnedButtons(isVisible: notification.status == .pending) {
            PrimaryButton(L10n.Notifications.userWithdrawButton, style: .destructive) {
                isWithdrawConfirmationVisible = true
            }
            .disabled(isWithdrawing)
        }
        .scrollContentBackground(.hidden)
        .contentMargins(.top, 0, for: .scrollContent)
        .background(Color(.systemGroupedBackground))
        .navigationTitle(title)
        .navigationBarTitleDisplayMode(.inline)
        .task { await onShown() }
        .alert(L10n.Notifications.userWithdrawConfirmTitle, isPresented: $isWithdrawConfirmationVisible) {
            Button(L10n.Common.buttonCancel, role: .cancel) {}
            Button(L10n.Notifications.userWithdrawButton, role: .destructive) {
                Task { await withdraw() }
            }
        }
        .alert(L10n.Common.errorUnknown, isPresented: $isWithdrawErrorVisible) {
            Button(L10n.Common.ok, role: .cancel) {}
        }
    }

    // MARK: - Functions

    private var statusColor: Color {
        switch notification.status {
        case .pending: .primary
        case .accepted: .success
        case .rejected: .error
        }
    }

    private func withdraw() async {
        isWithdrawing = true
        defer { isWithdrawing = false }
        switch await onWithdraw() {
        case .withdrawn, .alreadyResolved:
            dismiss()
        case .failed:
            isWithdrawErrorVisible = true
        }
    }
}

#if DEBUG
// MARK: - Preview

#Preview("Report") {
    NavigationStack {
        UserNotificationDetailView(
            title: "Rohlík",
            notification: UserNotificationDomain(
                kind: .report,
                id: "ticket-1",
                itemId: "8593807012345",
                createdAt: .now,
                reason: "Na obalu je 12 g bílkovin, v aplikaci 8 g."
            ),
            onShown: {},
            onWithdraw: { .withdrawn }
        )
    }
}

#Preview("Photo suggestion") {
    NavigationStack {
        UserNotificationDetailView(
            title: "Rohlík",
            notification: UserNotificationDomain(
                kind: .photoSuggestion,
                id: "ticket-2",
                itemId: "8593807012345",
                createdAt: .now,
                photoURL: URL(fileURLWithPath: "/tmp/none.jpg")
            ),
            onShown: {},
            onWithdraw: { .withdrawn }
        )
    }
}

#Preview("Resolved report") {
    NavigationStack {
        UserNotificationDetailView(
            title: "Rohlík",
            notification: UserNotificationDomain(
                kind: .report,
                id: "ticket-3",
                itemId: "8593807012345",
                createdAt: .now.addingTimeInterval(-86_400),
                status: .accepted,
                response: "Díky za nahlášení! Údaje u potraviny jsem opravil.",
                resolvedAt: .now,
                reason: "Na obalu je 12 g bílkovin, v aplikaci 8 g."
            ),
            onShown: {},
            onWithdraw: { .withdrawn }
        )
    }
}
#endif
