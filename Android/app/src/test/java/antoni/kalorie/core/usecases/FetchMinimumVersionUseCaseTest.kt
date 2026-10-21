package antoni.kalorie.core.usecases

import antoni.kalorie.core.models.AppVersion
import antoni.kalorie.core.networking.AppConfigDTO
import antoni.kalorie.core.networking.FirestoreDataProviderFake
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test

class FetchMinimumVersionUseCaseTest {

    // MARK: - Tests

    @Test
    fun fetch_readsTheAppConfigDocumentFromTheServer() = runTest {
        val (sut, dataProvider) = makeSUT()

        sut()

        assertEquals("config", dataProvider.queriedServerCollection)
        assertEquals("app", dataProvider.queriedServerId)
    }

    @Test
    fun fetch_returnsTheAndroidField() = runTest {
        val (sut, _) = makeSUT(config = AppConfigDTO(minVersionIos = "9.0", minVersionAndroid = "1.0.1"))

        assertEquals(AppVersion.parse("1.0.1"), sut())
    }

    @Test
    fun fetch_whenDocumentIsMissing_returnsNull() = runTest {
        val (sut, _) = makeSUT(config = null)

        assertNull(sut())
    }

    @Test
    fun fetch_whenAndroidFieldIsAbsent_returnsNull() = runTest {
        val (sut, _) = makeSUT(config = AppConfigDTO(minVersionIos = "9.0", minVersionAndroid = null))

        assertNull(sut())
    }

    @Test
    fun fetch_whenAndroidFieldDoesNotParse_returnsNull() = runTest {
        val (sut, _) = makeSUT(config = AppConfigDTO(minVersionIos = null, minVersionAndroid = "1.1-beta"))

        assertNull(sut())
    }

    @Test
    fun fetch_whenReadFails_propagatesTheError() = runTest {
        val (sut, dataProvider) = makeSUT()
        dataProvider.stubbedError = RuntimeException("offline")

        try {
            sut()
            fail("Expected the read error")
        } catch (_: RuntimeException) {
        }
    }

    // MARK: - Helpers

    private fun makeSUT(config: AppConfigDTO? = null): Pair<FetchMinimumVersionUseCase, FirestoreDataProviderFake> {
        val dataProvider = FirestoreDataProviderFake()
        dataProvider.stubbedServerDocument = config
        return FetchMinimumVersionUseCase(dataProvider) to dataProvider
    }
}
