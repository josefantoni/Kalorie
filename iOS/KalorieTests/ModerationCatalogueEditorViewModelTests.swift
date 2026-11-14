//
//  ModerationCatalogueEditorViewModelTests.swift
//  KalorieTests
//
//  Created by Josef Antoni on 11.09.2026.
//

import XCTest
@testable import Kalorie

final class ModerationCatalogueEditorViewModelTests: XCTestCase {

    @MainActor
    func test_onSearchTapped_whenFound_prefillsFormAndHidesPreviousCheckmark() async {
        let item = makeItem()
        let sut = makeSUT(fetchFoodItemByBarcode: FetchFoodItemByBarcodeUseCaseFake(stubbedItem: item))
        sut.barcodeQuery = "12345678"
        await sut.onSearchTapped()
        XCTAssertEqual(sut.formInput.scannedCode, "12345678")
        XCTAssertFalse(sut.showCheckmark)
        XCTAssertNil(sut.alertItem)
    }

    @MainActor
    func test_onSearchTapped_whenNotFound_showsAlert() async {
        let sut = makeSUT(fetchFoodItemByBarcode: FetchFoodItemByBarcodeUseCaseFake(stubbedItem: nil))
        sut.barcodeQuery = "12345678"
        await sut.onSearchTapped()
        XCTAssertEqual(sut.alertItem?.title, L10n.AddFood.errorBarcodeNotFound)
    }

    @MainActor
    func test_onSaveTapped_preservesTheOriginalItemsDate() async {
        let originalDate = Date(timeIntervalSince1970: 1_700_000_000)
        let item = makeItem(date: originalDate)
        let updateFoodItem = UpdateFoodItemUseCaseSpy()
        let sut = makeSUT(
            fetchFoodItemByBarcode: FetchFoodItemByBarcodeUseCaseFake(stubbedItem: item),
            updateFoodItem: updateFoodItem
        )
        sut.barcodeQuery = "12345678"
        await sut.onSearchTapped()

        sut.formInput.name = "Opravený název"
        await sut.onSaveTapped()

        XCTAssertEqual(updateFoodItem.receivedItem?.date, originalDate, "correcting a field must not silently reset the item's original date")
    }

    @MainActor
    func test_onSaveTapped_forBarcodeLessItem_keepsItsUUIDAsTheItemId() async {
        let uuid = "9A5E1B2C-8D3F-4A6E-9C1D-7B2A4E5F6C8D"
        let updateFoodItem = UpdateFoodItemUseCaseSpy()
        let sut = makeSUT(
            fetchFoodItemByBarcode: FetchFoodItemByBarcodeUseCaseFake(stubbedItem: makeItem(id: uuid)),
            updateFoodItem: updateFoodItem
        )
        sut.barcodeQuery = uuid
        await sut.onSearchTapped()

        await sut.onSaveTapped()

        XCTAssertEqual(updateFoodItem.receivedItem?.id, uuid, "a barcode-less item's identity is its UUID; saving a correction must not replace it with an empty id")
    }

    @MainActor
    func test_onSaveTapped_whenItemChangedSinceLoad_showsAlertAndDoesNotShowCheckmark() async {
        let item = makeItem()
        let updateFoodItem = UpdateFoodItemUseCaseFake(errorToThrow: UpdateFoodItemError.changedSinceLoad)
        let sut = makeSUT(
            fetchFoodItemByBarcode: FetchFoodItemByBarcodeUseCaseFake(stubbedItem: item),
            updateFoodItem: updateFoodItem
        )
        sut.barcodeQuery = "12345678"
        await sut.onSearchTapped()

        sut.formInput.name = "Opravený název"
        await sut.onSaveTapped()

        XCTAssertEqual(sut.alertItem?.title, L10n.Moderation.errorItemChangedSinceLoad)
        XCTAssertFalse(sut.showCheckmark)
    }

    @MainActor
    func test_onNutritionLabelRecognized_onSuccess_closesCameraAndMergesWithoutTouchingFilledFields() async {
        let item = makeItem()
        let sut = makeSUT(fetchFoodItemByBarcode: FetchFoodItemByBarcodeUseCaseFake(stubbedItem: item))
        sut.barcodeQuery = "12345678"
        await sut.onSearchTapped()
        sut.isNutritionLabelCameraVisible = true
        let originalFat = sut.formInput.fat

        await sut.onNutritionLabelRecognized(NutritionLabelReading(fat: 999, fiber: 1), ocrText: "", liveBarcode: nil)

        XCTAssertFalse(sut.isNutritionLabelCameraVisible, "a successful capture on an already-open form must close the camera without a push")
        XCTAssertEqual(sut.formInput.fat, originalFat, "a field already holding a loaded value must never be overwritten by the photo")
        XCTAssertEqual(sut.formInput.fiber, 1, "a field still at its default must be filled")
    }

    @MainActor
    func test_onAppear_withATicket_prefillsFormFromItsItem() async {
        let item = makeItem()
        let sut = makeSUT(fetchFoodItemByBarcode: FetchFoodItemByBarcodeUseCaseFake(stubbedItem: item), ticket: makeReport())

        await sut.onAppear()

        XCTAssertEqual(sut.loadedItem, item, "a report row must land the maintainer on the already-loaded item, not an empty search field")
    }

    @MainActor
    func test_onAppear_withoutATicket_doesNothing() async {
        let sut = makeSUT()

        await sut.onAppear()

        XCTAssertNil(sut.loadedItem)
        XCTAssertFalse(sut.isOpenedFromReport, "the plain Save button belongs to the editor opened without a ticket")
    }

    @MainActor
    func test_onSaveAndResolveTapped_savesFirst_thenOpensTheSheetPrefilledInTheTicketsLocale() async {
        let updateFoodItem = UpdateFoodItemUseCaseSpy()
        let resolve = ResolveFoodItemReportUseCaseSpy()
        let sut = makeSUT(
            fetchFoodItemByBarcode: FetchFoodItemByBarcodeUseCaseFake(stubbedItem: makeItem()),
            updateFoodItem: updateFoodItem,
            resolveFoodItemReport: resolve,
            ticket: makeReport(locale: "cs")
        )
        await sut.onAppear()
        sut.formInput.name = "Opravený název"

        await sut.onSaveAndResolveTapped()

        XCTAssertEqual(updateFoodItem.receivedItem?.czName, "Opravený název")
        XCTAssertEqual(sut.responseDraft?.status, .accepted)
        XCTAssertEqual(sut.responseDraft?.text, "Díky za nahlášení! Údaje u potraviny jsem opravil.", "the author reads it in their language, not the maintainer's")
        XCTAssertTrue(resolve.calls.isEmpty, "the ticket is resolved only when the maintainer sends the response")
        XCTAssertFalse(sut.shouldDismiss)
    }

    @MainActor
    func test_onSaveAndResolveTapped_whenSaveFails_doesNotOpenTheSheetOrResolve() async {
        let resolve = ResolveFoodItemReportUseCaseSpy()
        let sut = makeSUT(
            fetchFoodItemByBarcode: FetchFoodItemByBarcodeUseCaseFake(stubbedItem: makeItem()),
            updateFoodItem: UpdateFoodItemUseCaseFake(errorToThrow: UpdateFoodItemError.changedSinceLoad),
            resolveFoodItemReport: resolve,
            ticket: makeReport()
        )
        await sut.onAppear()

        await sut.onSaveAndResolveTapped()

        XCTAssertNil(sut.responseDraft, "resolving a report whose correction was not saved would close it with the item still wrong")
        XCTAssertTrue(resolve.calls.isEmpty)
        XCTAssertEqual(sut.alertItem?.title, L10n.Moderation.errorItemChangedSinceLoad)
    }

    @MainActor
    func test_onRejectTapped_opensAnEmptySheetWithoutSavingTheForm() async {
        let updateFoodItem = UpdateFoodItemUseCaseSpy()
        let sut = makeSUT(
            fetchFoodItemByBarcode: FetchFoodItemByBarcodeUseCaseFake(stubbedItem: makeItem()),
            updateFoodItem: updateFoodItem,
            ticket: makeReport()
        )
        await sut.onAppear()
        sut.formInput.name = "Rozpracovaná úprava"

        sut.onRejectTapped()

        XCTAssertNil(updateFoodItem.receivedItem, "rejecting a report judges it wrong, so the catalogue entry must stay untouched")
        XCTAssertEqual(sut.responseDraft?.status, .rejected)
        XCTAssertEqual(sut.responseDraft?.isSendable, false, "a rejection must be explained to the author")
    }

    @MainActor
    func test_onResponseSendTapped_resolvesTheTicketWithTheEditedResponse_andDismisses() async {
        let resolve = ResolveFoodItemReportUseCaseSpy()
        var resolved = 0
        let sut = makeSUT(
            fetchFoodItemByBarcode: FetchFoodItemByBarcodeUseCaseFake(stubbedItem: makeItem()),
            resolveFoodItemReport: resolve,
            ticket: makeReport(id: "report-7")
        ) { resolved += 1 }
        await sut.onAppear()
        await sut.onSaveAndResolveTapped()
        sut.responseDraft?.text = "Fixed it"

        await sut.onResponseSendTapped()

        XCTAssertEqual(resolve.calls, [.init(ticketId: "report-7", status: .accepted, response: "Fixed it")])
        XCTAssertEqual(resolved, 1, "the parent list and the bell must learn the ticket is gone from the queue")
        XCTAssertNil(sut.responseDraft)
        XCTAssertTrue(sut.shouldDismiss)
    }

    @MainActor
    func test_onResponseSendTapped_whenTheTicketWriteFails_keepsTheItemSavedAndTheSheetOpenForARetry() async {
        let resolve = ResolveFoodItemReportUseCaseSpy()
        resolve.error = URLError(.notConnectedToInternet)
        var resolved = 0
        let sut = makeSUT(
            fetchFoodItemByBarcode: FetchFoodItemByBarcodeUseCaseFake(stubbedItem: makeItem()),
            resolveFoodItemReport: resolve,
            ticket: makeReport()
        ) { resolved += 1 }
        await sut.onAppear()
        await sut.onSaveAndResolveTapped()

        await sut.onResponseSendTapped()

        XCTAssertNotNil(sut.responseDraft, "the maintainer must not have to retype the response")
        XCTAssertNotNil(sut.responseDraft?.errorTitle)
        XCTAssertEqual(resolved, 0)
        XCTAssertFalse(sut.shouldDismiss)

        resolve.error = nil
        await sut.onResponseSendTapped()

        XCTAssertEqual(resolve.calls.count, 1)
        XCTAssertTrue(sut.shouldDismiss)
    }

    @MainActor
    func test_onResponseSendTapped_withEmptyText_doesNotWrite() async {
        let resolve = ResolveFoodItemReportUseCaseSpy()
        let sut = makeSUT(resolveFoodItemReport: resolve, ticket: makeReport())
        sut.onRejectTapped()

        await sut.onResponseSendTapped()

        XCTAssertTrue(resolve.calls.isEmpty)
        XCTAssertNotNil(sut.responseDraft)
    }

    @MainActor
    func test_onSaveAndResolveTapped_whileASaveIsRunning_isIgnored() async {
        let updateFoodItem = SuspendingUpdateFoodItemUseCase()
        let sut = makeSUT(
            fetchFoodItemByBarcode: FetchFoodItemByBarcodeUseCaseFake(stubbedItem: makeItem()),
            updateFoodItem: updateFoodItem,
            ticket: makeReport()
        )
        await sut.onAppear()

        let first = Task { await sut.onSaveAndResolveTapped() }
        while updateFoodItem.callCount == 0 { await Task.yield() }
        await sut.onSaveAndResolveTapped()
        sut.onRejectTapped()
        updateFoodItem.release()
        await first.value

        XCTAssertEqual(updateFoodItem.callCount, 1, "a double tap must not save twice")
        XCTAssertEqual(sut.responseDraft?.status, .accepted)
    }

    // MARK: - Helpers

    private func makeSUT(
        fetchFoodItemByBarcode: any FetchFoodItemByBarcodeUseCaseProtocol = FetchFoodItemByBarcodeUseCaseFake(),
        updateFoodItem: any UpdateFoodItemUseCaseProtocol = UpdateFoodItemUseCaseFake(),
        resolveFoodItemReport: any ResolveFoodItemReportUseCaseProtocol = ResolveFoodItemReportUseCaseFake(),
        modelExtractor: any NutritionLabelModelExtractorProtocol = NutritionLabelModelExtractorFake(),
        cameraAuthorizationProvider: any CameraAuthorizationProviderProtocol = CameraAuthorizationProviderFake(),
        ticket: FoodItemReportDomain? = nil,
        onResolved: @escaping () -> Void = {}
    ) -> ModerationCatalogueEditorViewModel {
        ModerationCatalogueEditorViewModel(
            fetchFoodItemByBarcode: fetchFoodItemByBarcode,
            updateFoodItem: updateFoodItem,
            resolveFoodItemReport: resolveFoodItemReport,
            modelExtractor: modelExtractor,
            cameraAuthorizationProvider: cameraAuthorizationProvider,
            ticket: ticket,
            onResolved: onResolved
        )
    }

    private func makeItem(id: String = "12345678", date: Date = .now) -> FoodItemDomain {
        FoodItemDomain(
            id: id,
            kind: .catalogue,
            czName: "Tvaroh",
            engName: "Cottage cheese",
            weight: 200,
            date: date,
            energyKJ: 335,
            caloriesPerHundredGrams: 80,
            fat: 0.5,
            fatSaturated: 0.3,
            fatUnsaturatedFattyAcids: 0.2,
            carbohydrate: 4,
            carbohydratePureSugar: 3,
            fiber: 0,
            protein: 13,
            salt: 0.1
        )
    }
}

private final class UpdateFoodItemUseCaseSpy: UpdateFoodItemUseCaseProtocol {

    // MARK: - Properties

    private(set) var receivedItem: FoodItemDomain?
    private(set) var receivedPhoto: FoodItemFormPhoto?

    // MARK: - Functions

    func callAsFunction(_ item: FoodItemDomain, previouslyLoaded: FoodItemDomain, photo: FoodItemFormPhoto, validate: Bool) async throws -> FoodItemDomain {
        receivedItem = item
        receivedPhoto = photo
        return item
    }
}

private final class SuspendingUpdateFoodItemUseCase: UpdateFoodItemUseCaseProtocol {

    // MARK: - Properties

    private(set) var callCount = 0
    private var continuation: CheckedContinuation<Void, Never>?

    // MARK: - Functions

    func callAsFunction(_ item: FoodItemDomain, previouslyLoaded: FoodItemDomain, photo: FoodItemFormPhoto, validate: Bool) async throws -> FoodItemDomain {
        callCount += 1
        await withCheckedContinuation { continuation = $0 }
        return item
    }

    func release() {
        continuation?.resume()
        continuation = nil
    }
}

private final class ResolveFoodItemReportUseCaseSpy: ResolveFoodItemReportUseCaseProtocol {

    // MARK: - Types

    struct Call: Equatable {
        let ticketId: String
        let status: TicketStatus
        let response: String
    }

    // MARK: - Properties

    private(set) var calls: [Call] = []
    var error: Error?

    // MARK: - Functions

    func callAsFunction(ticketId: String, status: TicketStatus, response: String) async throws {
        if let error { throw error }
        calls.append(Call(ticketId: ticketId, status: status, response: response))
    }
}
