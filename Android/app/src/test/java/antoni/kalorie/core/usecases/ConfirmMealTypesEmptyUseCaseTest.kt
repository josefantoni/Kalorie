package antoni.kalorie.core.usecases

import antoni.kalorie.core.auth.AuthProviderFake
import antoni.kalorie.core.networking.FirestoreDataProviderFake
import antoni.kalorie.core.networking.MealTypeDTO
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ConfirmMealTypesEmptyUseCaseTest {

    // MARK: - Tests

    @Test
    fun callAsFunction_whenServerReturnsEmpty_returnsTrue() = runTest {
        val (sut, _) = makeSUT()

        assertTrue(sut())
    }

    @Test
    fun callAsFunction_whenServerReturnsMealTypes_returnsFalse() = runTest {
        val (sut, dataProvider) = makeSUT()
        dataProvider.stubbedServerDocuments = listOf(MealTypeDTO(id = "0", name = "Snídaně", startMinutes = 0, endMinutes = 60))

        assertFalse(sut())
    }

    @Test
    fun callAsFunction_whenServerUnreachable_throwsError() = runTest {
        val (sut, dataProvider) = makeSUT()
        dataProvider.stubbedError = RuntimeException("not connected to the internet")

        try {
            sut()
            fail("Expected error to be thrown")
        } catch (_: RuntimeException) {
        }
    }

    @Test
    fun callAsFunction_usesServerSourceNotCache() = runTest {
        val (sut, dataProvider) = makeSUT()
        dataProvider.stubbedServerDocuments = listOf(MealTypeDTO(id = "0", name = "Snídaně", startMinutes = 0, endMinutes = 60))
        dataProvider.stubbedDocuments = emptyList()

        assertFalse("musí číst ze serveru, ne z (potenciálně zastaralé) cache", sut())
    }

    // MARK: - Helpers

    private fun makeSUT(): Pair<ConfirmMealTypesEmptyUseCase, FirestoreDataProviderFake> {
        val dataProvider = FirestoreDataProviderFake()
        val sut = ConfirmMealTypesEmptyUseCase(dataProvider = dataProvider, authProvider = AuthProviderFake())
        return sut to dataProvider
    }
}
