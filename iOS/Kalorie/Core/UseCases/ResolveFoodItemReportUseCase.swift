//
//  ResolveFoodItemReportUseCase.swift
//  Kalorie
//
//  Created by Josef Antoni on 14.11.2026.
//

import Foundation

protocol ResolveFoodItemReportUseCaseProtocol {
    func callAsFunction(ticketId: String, status: TicketStatus, response: String) async throws
}

struct ResolveFoodItemReportUseCase: ResolveFoodItemReportUseCaseProtocol {

    // MARK: - Properties

    private let authProvider: any AuthProviderProtocol
    private let writer: TicketResolutionWriter

    // MARK: - Init

    init(dataProvider: any FirestoreDataProviderProtocol, authProvider: any AuthProviderProtocol) {
        self.authProvider = authProvider
        self.writer = TicketResolutionWriter(dataProvider: dataProvider)
    }

    // MARK: - Functions

    func callAsFunction(ticketId: String, status: TicketStatus, response: String) async throws {
        guard authProvider.userId != nil else { throw AuthError.notAuthenticated }
        let trimmedResponse = try TicketResolutionWriter.validated(response)
        try await writer.write(
            status: status,
            response: trimmedResponse,
            ticketId: ticketId,
            in: Constants.Firestore.foodItemReports
        )
    }
}

#if DEBUG
struct ResolveFoodItemReportUseCaseFake: ResolveFoodItemReportUseCaseProtocol {

    // MARK: - Properties

    var errorToThrow: Error?

    // MARK: - Functions

    func callAsFunction(ticketId: String, status: TicketStatus, response: String) async throws {
        if let errorToThrow { throw errorToThrow }
    }
}
#endif
