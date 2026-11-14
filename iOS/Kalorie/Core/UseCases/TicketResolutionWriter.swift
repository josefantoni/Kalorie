//
//  TicketResolutionWriter.swift
//  Kalorie
//
//  Created by Josef Antoni on 14.11.2026.
//

import Foundation

enum TicketResolutionError: Error, Equatable {
    case responseRequired
    case responseTooLong
}

struct TicketResolutionWriter {

    // MARK: - Properties

    private let dataProvider: any FirestoreDataProviderProtocol

    // MARK: - Init

    init(dataProvider: any FirestoreDataProviderProtocol) {
        self.dataProvider = dataProvider
    }

    // MARK: - Functions

    static func validated(_ response: String) throws -> String {
        let trimmed = response.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { throw TicketResolutionError.responseRequired }
        // Rules' string.size() counts UTF-16 code units — an emoji is one Character but two or more units.
        guard trimmed.utf16.count <= Constants.Firestore.responseMaxLength else {
            throw TicketResolutionError.responseTooLong
        }
        return trimmed
    }

    func write(status: TicketStatus, response: String, ticketId: String, in collection: String) async throws {
        try await dataProvider.updateAsync(
            TicketResolutionDTO(status: status, response: response, resolvedAt: .now),
            id: ticketId,
            in: collection
        )
    }
}
