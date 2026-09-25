package antoni.kalorie.core.usecases

import antoni.kalorie.core.auth.AuthError
import antoni.kalorie.core.auth.AuthProviderFake
import antoni.kalorie.core.models.FoodItemDomain
import antoni.kalorie.core.models.FoodItemKind
import antoni.kalorie.core.models.FoodItemSubmissionStatus
import antoni.kalorie.core.networking.FirestoreDataProviderFake
import antoni.kalorie.core.networking.FoodItemSubmissionDTO
import antoni.kalorie.core.utils.Constants
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class FetchMySubmissionsUseCaseTest {

    // MARK: - Tests

    @Test
    fun fetchMySubmissions_whenNotAuthenticated_throwsAuthError() = runTest {
        val (sut, _) = makeSUT(userId = null)

        try {
            sut()
            fail("Expected notAuthenticated error")
        } catch (_: AuthError.NotAuthenticated) {
        }
    }

    @Test
    fun fetchMySubmissions_queriesOwnSubmissionsOrderedBySubmittedAtDescending() = runTest {
        val (sut, dataProvider) = makeSUT(userId = "user-123")

        sut()

        assertEquals(Constants.Firestore.FOOD_ITEM_SUBMISSIONS, dataProvider.queriedCollection)
        assertEquals("submitted_by", dataProvider.queriedField)
        assertEquals("user-123", dataProvider.queriedValue)
        assertEquals("submitted_at", dataProvider.queriedOrderByField)
        assertEquals(true, dataProvider.queriedDescending)
    }

    @Test
    fun fetchMySubmissions_mapsStubbedDTOsToDomains() = runTest {
        val (sut, dataProvider) = makeSUT()
        dataProvider.stubbedByField = listOf(makeDTO(id = "sub-1", barcode = "12345678"))

        val result = sut()

        assertEquals(listOf("sub-1"), result.map { it.id })
        assertEquals(listOf<String?>("12345678"), result.map { it.barcode })
    }

    // MARK: - Helpers

    private fun makeSUT(userId: String? = "test-user"): Pair<FetchMySubmissionsUseCase, FirestoreDataProviderFake> {
        val dataProvider = FirestoreDataProviderFake()
        return FetchMySubmissionsUseCase(dataProvider, AuthProviderFake(userId = userId)) to dataProvider
    }

    private fun makeDTO(id: String, barcode: String): FoodItemSubmissionDTO = FoodItemSubmissionDTO(
        id = id,
        barcode = barcode,
        submittedBy = "test-user",
        status = FoodItemSubmissionStatus.PENDING,
        submittedAt = Instant.now(),
        rejectReason = null,
        item = FoodItemDomain(
            id = barcode,
            kind = FoodItemKind.CATALOGUE,
            czName = "Tvaroh",
            engName = "",
            weight = 200.0,
            date = Instant.now(),
            energyKJ = 335.0,
            caloriesPerHundredGrams = 80.0,
            fat = 0.5,
            fatSaturated = 0.3,
            fatUnsaturatedFattyAcids = 0.2,
            carbohydrate = 4.0,
            carbohydratePureSugar = 3.0,
            fiber = 0.0,
            protein = 13.0,
            salt = 0.1,
        ),
    )
}
