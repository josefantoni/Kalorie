package antoni.kalorie.core.usecases

import antoni.kalorie.core.auth.AuthProviderFake
import antoni.kalorie.core.networking.FirestoreDataProviderFake
import antoni.kalorie.core.networking.MealTypeDTO
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerializationException
import org.junit.Assert.assertEquals
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

    // MARK: - Helpers

    private fun makeSUT(): Pair<FetchMealTypesUseCase, FirestoreDataProviderFake> {
        val dataProvider = FirestoreDataProviderFake()
        val sut = FetchMealTypesUseCase(dataProvider = dataProvider, authProvider = AuthProviderFake())
        return sut to dataProvider
    }
}
