package antoni.kalorie.core.usecases

import antoni.kalorie.core.networking.HttpResponse
import antoni.kalorie.core.networking.HttpSessionFake
import antoni.kalorie.core.utils.Constants
import java.io.IOException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlin.time.Duration
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test

class FetchFoodByBarcodeExternallyUseCaseTest {

    // MARK: - Tests

    @Test
    fun fetchByBarcode_withEmptyBarcode_returnsNilWithoutRequest() = runTest {
        val (sut, session) = makeSUT()

        val result = sut("")

        assertNull(result)
        assertNull(session.lastRequest)
    }

    @Test
    fun fetchByBarcode_withSuccessfulResponse_returnsMappedItem() = runTest {
        val (sut, session) = makeSUT()
        session.stubbedResults = listOf(Result.success(HttpResponse(makeBarcodeResponseBody(status = 1), 200)))

        val result = sut("1234567890123")

        assertEquals("1234567890123", result?.id)
    }

    @Test
    fun fetchByBarcode_withProductNotFoundStatus_returnsNil() = runTest {
        val (sut, session) = makeSUT()
        session.stubbedResults = listOf(Result.success(HttpResponse(makeBarcodeResponseBody(status = 0), 200)))

        val result = sut("1234567890123")

        assertNull(result)
    }

    @Test
    fun fetchByBarcode_setsUserAgentHeader() = runTest {
        val (sut, session) = makeSUT()
        session.stubbedResults = listOf(Result.success(HttpResponse(makeBarcodeResponseBody(status = 1), 200)))

        sut("1234567890123")

        assertEquals(Constants.OpenFoodFacts.USER_AGENT, session.lastRequest?.userAgent)
    }

    @Test
    fun fetchByBarcode_requestsTheDocumentedHostPathParametersAndTimeout() = runTest {
        val (sut, session) = makeSUT()
        session.stubbedResults = listOf(Result.success(HttpResponse(makeBarcodeResponseBody(status = 1), 200)))

        sut("1234567890123")

        assertEquals(
            "https://world.openfoodfacts.org/api/v2/product/1234567890123?fields=code,product_name,product_name_cs,product_name_en,nutriments",
            session.lastRequest?.url,
        )
        assertEquals(10_000, session.lastRequest?.timeoutMillis)
    }

    @Test
    fun fetchByBarcode_percentEncodesTheBarcodeAsAPathSegment() = runTest {
        val (sut, session) = makeSUT()
        session.stubbedResults = listOf(Result.success(HttpResponse(makeBarcodeResponseBody(status = 0), 200)))

        sut("12 3/4")

        assertEquals(
            "https://world.openfoodfacts.org/api/v2/product/12%203%2F4?fields=code,product_name,product_name_cs,product_name_en,nutriments",
            session.lastRequest?.url,
        )
    }

    @Test
    fun fetchByBarcode_withNonSuccessStatusCode_throwsServerErrorInsteadOfLookingLikeNotFound() = runTest {
        val (sut, session) = makeSUT()
        session.stubbedResults = List(Constants.OpenFoodFacts.MAX_ATTEMPTS) { Result.success(HttpResponse("Internal Server Error", 500)) }

        try {
            sut("1234567890123")
            fail("Expected FetchFoodByBarcodeExternallyError.ServerError to be thrown")
        } catch (error: FetchFoodByBarcodeExternallyError.ServerError) {
            assertEquals(500, error.statusCode)
        }
        assertEquals(Constants.OpenFoodFacts.MAX_ATTEMPTS, session.requestCount)
    }

    @Test
    fun fetchByBarcode_withTransientServerErrorThenSuccess_retriesAndReturnsMappedItem() = runTest {
        val (sut, session) = makeSUT()
        session.stubbedResults = listOf(
            Result.success(HttpResponse("Too Many Requests", 429)),
            Result.success(HttpResponse(makeBarcodeResponseBody(status = 1), 200)),
        )

        val result = sut("1234567890123")

        assertEquals("1234567890123", result?.id)
        assertEquals(2, session.requestCount)
    }

    @Test
    fun fetchByBarcode_withTimedOutThenSuccess_retriesAndReturnsMappedItem() = runTest {
        val (sut, session) = makeSUT()
        session.stubbedResults = listOf(
            Result.failure(SocketTimeoutException()),
            Result.success(HttpResponse(makeBarcodeResponseBody(status = 1), 200)),
        )

        val result = sut("1234567890123")

        assertEquals("1234567890123", result?.id)
        assertEquals(2, session.requestCount)
    }

    @Test
    fun fetchByBarcode_withPersistentNetworkConnectionLost_throwsAfterMaxAttempts() = runTest {
        val (sut, session) = makeSUT()
        session.stubbedResults = List(Constants.OpenFoodFacts.MAX_ATTEMPTS) { Result.failure(SocketException("Connection reset")) }

        try {
            sut("1234567890123")
            fail("Expected SocketException to be thrown")
        } catch (_: SocketException) {
        }
        assertEquals(Constants.OpenFoodFacts.MAX_ATTEMPTS, session.requestCount)
    }

    @Test
    fun fetchByBarcode_withNotConnectedToInternet_throwsWithoutRetrying() = runTest {
        val (sut, session) = makeSUT()
        session.stubbedResults = listOf(Result.failure(UnknownHostException()))

        try {
            sut("1234567890123")
            fail("Expected UnknownHostException to be thrown")
        } catch (_: IOException) {
        }
        assertEquals(1, session.requestCount)
    }

    // MARK: - Helpers

    private fun makeSUT(): Pair<FetchFoodByBarcodeExternallyUseCase, HttpSessionFake> {
        val session = HttpSessionFake()
        return FetchFoodByBarcodeExternallyUseCase(session = session, retryDelay = Duration.ZERO) to session
    }

    private fun makeBarcodeResponseBody(status: Int): String =
        if (status == 1) {
            """
            {
                "status": 1,
                "product": {
                    "code": "1234567890123",
                    "product_name_cs": "Mléko",
                    "nutriments": { "energy-kcal_100g": 60 }
                }
            }
            """.trimIndent()
        } else {
            """{ "status": 0 }"""
        }
}
