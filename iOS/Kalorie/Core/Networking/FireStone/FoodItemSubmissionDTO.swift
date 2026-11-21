//
//  FoodItemSubmissionDTO.swift
//  Kalorie
//
//  Created by Josef Antoni on 10.09.2026.
//

import Foundation

struct FoodItemSubmissionDTO: Codable {

    // MARK: - Properties

    let id: String
    let barcode: String?
    let submittedBy: String
    let status: FoodItemSubmissionStatus
    let submittedAt: TimeInterval
    let rejectReason: String?
    let nameLanguage: String
    let item: FoodItemDTO

    // MARK: - Coding keys

    enum CodingKeys: String, CodingKey {
        case id, barcode, status, item
        case submittedBy = "submitted_by"
        case submittedAt = "submitted_at"
        case rejectReason = "reject_reason"
        case nameLanguage = "name_language"
    }

    // MARK: - Init

    init(
        id: String,
        barcode: String?,
        submittedBy: String,
        status: FoodItemSubmissionStatus,
        submittedAt: Date,
        rejectReason: String?,
        nameLanguage: String,
        item: FoodItemDomain
    ) {
        self.id = id
        self.barcode = barcode
        self.submittedBy = submittedBy
        self.status = status
        self.submittedAt = submittedAt.timeIntervalSince1970
        self.rejectReason = rejectReason
        self.nameLanguage = nameLanguage
        self.item = FoodItemDTO(item: item)
    }

    // MARK: - Functions

    func asDomain() -> FoodItemSubmissionDomain {
        FoodItemSubmissionDomain(
            id: id,
            barcode: barcode,
            submittedBy: submittedBy,
            status: status,
            submittedAt: submittedAt.toDate,
            rejectReason: rejectReason,
            nameLanguage: nameLanguage,
            item: item.asDomain()
        )
    }
}
