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
    func test_onAppear_withInitialBarcode_prefillsFormFromIt() async {
        let item = makeItem()
        let sut = makeSUT(
            fetchFoodItemByBarcode: FetchFoodItemByBarcodeUseCaseFake(stubbedItem: item),
            initialBarcode: "12345678"
        )

        await sut.onAppear()

        XCTAssertEqual(sut.loadedItem, item, "a report row must land the maintainer on the already-loaded item, not an empty search field")
    }

    @MainActor
    func test_onAppear_withoutInitialBarcode_doesNothing() async {
        let sut = makeSUT()

        await sut.onAppear()

        XCTAssertNil(sut.loadedItem)
    }

    @MainActor
    func test_onSaveAndResolveTapped_savesThenDeletesEveryReportAndDismisses() async {
        let updateFoodItem = UpdateFoodItemUseCaseSpy()
        let deleteFoodItemReport = DeleteFoodItemReportUseCaseSpy()
        var changedReports: [[String]] = []
        let sut = makeSUT(
            fetchFoodItemByBarcode: FetchFoodItemByBarcodeUseCaseFake(stubbedItem: makeItem()),
            updateFoodItem: updateFoodItem,
            deleteFoodItemReport: deleteFoodItemReport,
            initialBarcode: "12345678",
            reports: [makeReport(reportedBy: "user-1"), makeReport(reportedBy: "user-2")]
        ) { changedReports.append($0.map(\.reportedBy)) }
        await sut.onAppear()

        sut.formInput.name = "Opravený název"
        await sut.onSaveAndResolveTapped()

        XCTAssertEqual(updateFoodItem.receivedItem?.czName, "Opravený název")
        XCTAssertEqual(deleteFoodItemReport.deletedReportedBy.sorted(), ["user-1", "user-2"])
        XCTAssertEqual(changedReports, [[]], "the parent list and the bell must learn the group is gone")
        XCTAssertTrue(sut.shouldDismiss)
    }

    @MainActor
    func test_onSaveAndResolveTapped_whenSaveFails_keepsTheReportsOpen() async {
        let deleteFoodItemReport = DeleteFoodItemReportUseCaseSpy()
        var changedReports: [[String]] = []
        let sut = makeSUT(
            fetchFoodItemByBarcode: FetchFoodItemByBarcodeUseCaseFake(stubbedItem: makeItem()),
            updateFoodItem: UpdateFoodItemUseCaseFake(errorToThrow: UpdateFoodItemError.changedSinceLoad),
            deleteFoodItemReport: deleteFoodItemReport,
            initialBarcode: "12345678",
            reports: [makeReport(reportedBy: "user-1")]
        ) { changedReports.append($0.map(\.reportedBy)) }
        await sut.onAppear()

        await sut.onSaveAndResolveTapped()

        XCTAssertTrue(deleteFoodItemReport.deletedReportedBy.isEmpty, "resolving a report whose correction was not saved would close it with the item still wrong")
        XCTAssertTrue(changedReports.isEmpty)
        XCTAssertFalse(sut.shouldDismiss)
        XCTAssertEqual(sut.alertItem?.title, L10n.Moderation.errorItemChangedSinceLoad)
    }

    @MainActor
    func test_onRejectTapped_deletesEveryReportWithoutSavingTheForm() async {
        let updateFoodItem = UpdateFoodItemUseCaseSpy()
        let deleteFoodItemReport = DeleteFoodItemReportUseCaseSpy()
        let sut = makeSUT(
            fetchFoodItemByBarcode: FetchFoodItemByBarcodeUseCaseFake(stubbedItem: makeItem()),
            updateFoodItem: updateFoodItem,
            deleteFoodItemReport: deleteFoodItemReport,
            initialBarcode: "12345678",
            reports: [makeReport(reportedBy: "user-1"), makeReport(reportedBy: "user-2")]
        )
        await sut.onAppear()

        sut.formInput.name = "Rozpracovaná úprava"
        await sut.onRejectTapped()

        XCTAssertNil(updateFoodItem.receivedItem, "rejecting a report judges it wrong, so the catalogue entry must stay untouched")
        XCTAssertEqual(deleteFoodItemReport.deletedReportedBy.sorted(), ["user-1", "user-2"])
        XCTAssertTrue(sut.shouldDismiss)
    }

    @MainActor
    func test_onRejectTapped_whenSomeDeletesFail_keepsOnlyTheFailedReportsAndDoesNotDismiss() async {
        var changedReports: [[String]] = []
        let sut = makeSUT(
            deleteFoodItemReport: DeleteFoodItemReportUseCaseSpy(failingReportedBy: ["user-2"]),
            initialBarcode: "12345678",
            reports: [makeReport(reportedBy: "user-1"), makeReport(reportedBy: "user-2")]
        ) { changedReports.append($0.map(\.reportedBy)) }

        await sut.onRejectTapped()

        XCTAssertEqual(
            sut.reports.map(\.reportedBy),
            ["user-2"],
            "a report that failed to delete still exists in Firestore — closing the screen would tell the maintainer it is resolved when it is not"
        )
        XCTAssertNotNil(sut.alertItem, "partial failure is accepted by design 0012 only because it is visible to the maintainer")
        XCTAssertEqual(changedReports, [["user-2"]], "the parent list and the bell must not keep reports that are already deleted")
        XCTAssertFalse(sut.shouldDismiss)
    }

    @MainActor
    func test_actions_whileAnotherActionIsRunning_areIgnored() async {
        let updateFoodItem = SuspendingUpdateFoodItemUseCase()
        let deleteFoodItemReport = DeleteFoodItemReportUseCaseSpy()
        var changedReports: [[String]] = []
        let sut = makeSUT(
            fetchFoodItemByBarcode: FetchFoodItemByBarcodeUseCaseFake(stubbedItem: makeItem()),
            updateFoodItem: updateFoodItem,
            deleteFoodItemReport: deleteFoodItemReport,
            initialBarcode: "12345678",
            reports: [makeReport(reportedBy: "user-1")]
        ) { changedReports.append($0.map(\.reportedBy)) }
        await sut.onAppear()

        let first = Task { await sut.onSaveAndResolveTapped() }
        while updateFoodItem.callCount == 0 { await Task.yield() }
        await sut.onSaveAndResolveTapped()
        await sut.onRejectTapped()
        updateFoodItem.release()
        await first.value

        XCTAssertEqual(updateFoodItem.callCount, 1, "a double tap must not save twice")
        XCTAssertEqual(deleteFoodItemReport.deletedReportedBy, ["user-1"])
        XCTAssertEqual(changedReports, [[]])
        XCTAssertNil(sut.alertItem)
    }

    @MainActor
    func test_onSaveAndResolveTapped_retryAfterPartialDeleteFailure_savesAgainWithoutChangedSinceLoad() async {
        let item = makeItem()
        let uploadedURL = URL(fileURLWithPath: "/uploaded.jpg")
        let updateFoodItem = StoringUpdateFoodItemUseCase(stored: item, uploadedURL: uploadedURL)
        let deleteFoodItemReport = DeleteFoodItemReportUseCaseSpy(failingReportedBy: ["user-2"])
        var changedReports: [[String]] = []
        let sut = makeSUT(
            fetchFoodItemByBarcode: FetchFoodItemByBarcodeUseCaseFake(stubbedItem: item),
            updateFoodItem: updateFoodItem,
            deleteFoodItemReport: deleteFoodItemReport,
            initialBarcode: "12345678",
            reports: [makeReport(reportedBy: "user-1"), makeReport(reportedBy: "user-2")]
        ) { changedReports.append($0.map(\.reportedBy)) }
        await sut.onAppear()
        sut.formInput.photo = .local(Data([1, 2]))

        await sut.onSaveAndResolveTapped()
        deleteFoodItemReport.failingReportedBy = []
        sut.alertItem = nil
        await sut.onSaveAndResolveTapped()

        XCTAssertNil(sut.alertItem, "Firestore already holds the uploaded photo, so the retry must compare against it, not the pre-upload copy")
        XCTAssertEqual(deleteFoodItemReport.deletedReportedBy.sorted(), ["user-1", "user-2"])
        XCTAssertEqual(changedReports, [["user-2"], []])
        XCTAssertTrue(sut.shouldDismiss)
    }

    // MARK: - Helpers

    private func makeSUT(
        fetchFoodItemByBarcode: any FetchFoodItemByBarcodeUseCaseProtocol = FetchFoodItemByBarcodeUseCaseFake(),
        updateFoodItem: any UpdateFoodItemUseCaseProtocol = UpdateFoodItemUseCaseFake(),
        deleteFoodItemReport: any DeleteFoodItemReportUseCaseProtocol = DeleteFoodItemReportUseCaseFake(),
        modelExtractor: any NutritionLabelModelExtractorProtocol = NutritionLabelModelExtractorFake(),
        cameraAuthorizationProvider: any CameraAuthorizationProviderProtocol = CameraAuthorizationProviderFake(),
        initialBarcode: String? = nil,
        reports: [FoodItemReportDomain] = [],
        onReportsChanged: @escaping ([FoodItemReportDomain]) -> Void = { _ in }
    ) -> ModerationCatalogueEditorViewModel {
        ModerationCatalogueEditorViewModel(
            fetchFoodItemByBarcode: fetchFoodItemByBarcode,
            updateFoodItem: updateFoodItem,
            deleteFoodItemReport: deleteFoodItemReport,
            modelExtractor: modelExtractor,
            cameraAuthorizationProvider: cameraAuthorizationProvider,
            initialBarcode: initialBarcode,
            reports: reports,
            onReportsChanged: onReportsChanged
        )
    }

    private func makeReport(reportedBy: String) -> FoodItemReportDomain {
        FoodItemReportDomain(barcode: "12345678", reportedBy: reportedBy, reason: "wrong data", reportedAt: .now)
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

private final class StoringUpdateFoodItemUseCase: UpdateFoodItemUseCaseProtocol {

    // MARK: - Properties

    private(set) var callCount = 0
    private var stored: FoodItemDomain
    private let uploadedURL: URL

    // MARK: - Init

    init(stored: FoodItemDomain, uploadedURL: URL) {
        self.stored = stored
        self.uploadedURL = uploadedURL
    }

    // MARK: - Functions

    func callAsFunction(_ item: FoodItemDomain, previouslyLoaded: FoodItemDomain, photo: FoodItemFormPhoto, validate: Bool) async throws -> FoodItemDomain {
        callCount += 1
        guard previouslyLoaded == stored else { throw UpdateFoodItemError.changedSinceLoad }
        if case .local = photo {
            stored = item.withPhotoURL(uploadedURL)
        } else {
            stored = item
        }
        return stored
    }
}

private final class DeleteFoodItemReportUseCaseSpy: DeleteFoodItemReportUseCaseProtocol {

    // MARK: - Properties

    private(set) var deletedReportedBy: [String] = []
    var failingReportedBy: Set<String>

    // MARK: - Init

    init(failingReportedBy: Set<String> = []) {
        self.failingReportedBy = failingReportedBy
    }

    // MARK: - Functions

    func callAsFunction(barcode: String, reportedBy: String) async throws {
        if failingReportedBy.contains(reportedBy) { throw URLError(.unknown) }
        deletedReportedBy.append(reportedBy)
    }
}
