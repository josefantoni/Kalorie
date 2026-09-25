package antoni.kalorie.core.usecases

import antoni.kalorie.core.auth.AuthError
import antoni.kalorie.core.auth.AuthProviderFake
import antoni.kalorie.core.models.FoodPortionDomain
import antoni.kalorie.core.models.FoodPortionError
import antoni.kalorie.core.models.FoodPortionValidation
import antoni.kalorie.core.networking.FirestoreDataProviderFake
import antoni.kalorie.core.networking.FoodItemPersonalPortionsDTO
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test

class SaveFoodItemPersonalPortionsUseCaseTest {

    // MARK: - Tests

    @Test
    fun save_whenNotAuthenticated_throwsAuthErrorAndDoesNotWrite() = runTest {
        val (sut, dataProvider) = makeSUT(userId = null)

        try {
            sut("12345678", listOf(makePortion()))
            fail("Expected notAuthenticated error")
        } catch (_: AuthError.NotAuthenticated) {
        }
        assertNull(dataProvider.savedDTO)
    }

    @Test
    fun save_withEmptyName_throwsInvalidNameAndDoesNotWrite() = runTest {
        val (sut, dataProvider) = makeSUT()

        try {
            sut("12345678", listOf(makePortion(name = "")))
            fail("Expected invalidName error")
        } catch (_: FoodPortionError.InvalidName) {
        }
        assertNull(dataProvider.savedDTO)
    }

    @Test
    fun save_withGramsBelowOne_throwsInvalidGramsAndDoesNotWrite() = runTest {
        val (sut, dataProvider) = makeSUT()

        try {
            sut("12345678", listOf(makePortion(grams = 0.0)))
            fail("Expected invalidGrams error")
        } catch (_: FoodPortionError.InvalidGrams) {
        }
        assertNull(dataProvider.savedDTO)
    }

    @Test
    fun save_withTooManyPortions_throwsTooManyAndDoesNotWrite() = runTest {
        val (sut, dataProvider) = makeSUT()
        val portions = (0..FoodPortionValidation.MAX_PORTIONS).map { makePortion(name = "Porce $it") }

        try {
            sut("12345678", portions)
            fail("Expected tooMany error")
        } catch (_: FoodPortionError.TooMany) {
        }
        assertNull(dataProvider.savedDTO)
    }

    @Test
    fun save_withValidPortions_writesWholeListToUserSpecificDocument() = runTest {
        val (sut, dataProvider) = makeSUT(userId = "user-123")

        sut("12345678", listOf(makePortion(name = "1 balení", grams = 33.0)))

        assertEquals("users/user-123/foodItemPortions", dataProvider.setSavedCollection)
        assertEquals("12345678", dataProvider.setSavedId)
        assertEquals("12345678", dataProvider.savedDTO?.id)
        assertEquals(listOf("1 balení"), dataProvider.savedDTO?.portions?.map { it.name })
    }

    // MARK: - Helpers

    private val FirestoreDataProviderFake.savedDTO: FoodItemPersonalPortionsDTO?
        get() = setSavedItem as? FoodItemPersonalPortionsDTO

    private fun makeSUT(userId: String? = "test-user"): Pair<SaveFoodItemPersonalPortionsUseCase, FirestoreDataProviderFake> {
        val dataProvider = FirestoreDataProviderFake()
        return SaveFoodItemPersonalPortionsUseCase(dataProvider, AuthProviderFake(userId = userId)) to dataProvider
    }

    private fun makePortion(name: String = "1 balení", grams: Double = 33.0): FoodPortionDomain = FoodPortionDomain(name = name, grams = grams)
}
