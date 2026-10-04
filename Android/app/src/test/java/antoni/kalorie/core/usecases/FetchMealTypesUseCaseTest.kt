package antoni.kalorie.core.usecases

import antoni.kalorie.R
import antoni.kalorie.core.auth.AuthProviderFake
import antoni.kalorie.core.networking.FirestoreDataProviderFake
import antoni.kalorie.core.networking.MealTypeDTO
import antoni.kalorie.core.utils.StringProviderFake
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerializationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class FetchMealTypesUseCaseTest {

    // MARK: - Tests

    @Test
    fun fetchMealTypes_withEmptyProvider_returnsEmptyArray() = runTest {
        val (sut, _) = makeSUT()

        val result = sut()

        assertTrue(result.isEmpty())
    }

    @Test
    fun fetchMealTypes_returnsMappedAndSortedDomains() = runTest {
        val (sut, dataProvider) = makeSUT()
        dataProvider.stubbedDocuments = listOf(
            MealTypeDTO(id = "1", name = "Oběd", startMinutes = 12 * 60, endMinutes = 14 * 60),
            MealTypeDTO(id = "0", name = "Snídaně", startMinutes = 6 * 60, endMinutes = 9 * 60),
        )

        val result = sut()

        assertEquals(2, result.size)
        assertEquals("Snídaně", result[0].name)
        assertEquals("Oběd", result[1].name)
    }

    @Test
    fun fetchMealTypes_returnsStoredMinutesUnchanged() = runTest {
        val (sut, dataProvider) = makeSUT()
        dataProvider.stubbedDocuments = listOf(
            MealTypeDTO(id = "0", name = "Snídaně", startMinutes = 420, endMinutes = 1410),
        )

        val result = sut()

        assertEquals(420, result[0].startMinutes)
        assertEquals(1410, result[0].endMinutes)
    }

    @Test
    fun fetchMealTypes_whenProviderThrowsDecodingError_throwsError() = runTest {
        val (sut, dataProvider) = makeSUT()
        dataProvider.stubbedError = SerializationException("test")

        try {
            sut()
            fail("Expected SerializationException to be thrown")
        } catch (_: SerializationException) {
        }
    }

    @Test
    fun fetchMealTypes_withKnownDefaultKey_resolvesCurrentLanguageNameIgnoringStoredName() = runTest {
        val (sut, dataProvider) = makeSUT()
        dataProvider.stubbedDocuments = listOf(
            MealTypeDTO(id = "0", name = "stored in another language", startMinutes = 300, endMinutes = 510, defaultKey = "breakfast"),
        )

        val result = sut()

        assertEquals("a default meal must follow the language of the device showing it", StringProviderFake().getString(R.string.defaultMeals_breakfast), result[0].name)
        assertEquals("breakfast", result[0].defaultKey)
    }

    @Test
    fun fetchMealTypes_withoutDefaultKey_keepsStoredName() = runTest {
        val (sut, dataProvider) = makeSUT()
        dataProvider.stubbedDocuments = listOf(
            MealTypeDTO(id = "0", name = "Brunch", startMinutes = 300, endMinutes = 510),
        )

        val result = sut()

        assertEquals("a user-named meal must show exactly what the user typed", "Brunch", result[0].name)
        assertNull(result[0].defaultKey)
    }

    @Test
    fun fetchMealTypes_withUnknownDefaultKey_keepsStoredName() = runTest {
        val (sut, dataProvider) = makeSUT()
        dataProvider.stubbedDocuments = listOf(
            MealTypeDTO(id = "0", name = "Brunch", startMinutes = 300, endMinutes = 510, defaultKey = "brunch"),
        )

        val result = sut()

        assertEquals("a key from a newer client must not blank out the name", "Brunch", result[0].name)
    }

    // MARK: - Helpers

    private fun makeSUT(): Pair<FetchMealTypesUseCase, FirestoreDataProviderFake> {
        val dataProvider = FirestoreDataProviderFake()
        val sut = FetchMealTypesUseCase(dataProvider = dataProvider, authProvider = AuthProviderFake(), stringProvider = StringProviderFake())
        return sut to dataProvider
    }
}
