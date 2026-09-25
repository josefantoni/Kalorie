package antoni.kalorie.core.usecases

import antoni.kalorie.core.networking.HttpResponse
import antoni.kalorie.core.networking.HttpSessionFake
import antoni.kalorie.core.utils.Constants
import java.io.IOException
import java.net.ConnectException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlin.time.Duration
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class SearchFoodExternallyUseCaseTest {

    // MARK: - Tests

    @Test
    fun search_withSuccessfulResponse_returnsMappedItems() = runTest {
        val (sut, session) = makeSUT()
        session.stubbedResults = listOf(Result.success(HttpResponse(makeSearchResponseBody(), 200)))

        val items = sut("mleko")

        assertEquals(1, items.size)
        assertEquals("1234567890123", items.first().id)
    }

    @Test
    fun search_setsUserAgentHeader() = runTest {
        val (sut, session) = makeSUT()
        session.stubbedResults = listOf(Result.success(HttpResponse(makeSearchResponseBody(), 200)))

        sut("mleko")

        assertEquals(Constants.OpenFoodFacts.USER_AGENT, session.lastRequest?.userAgent)
    }

    @Test
    fun search_requestsTheDocumentedHostPathParametersAndTimeout() = runTest {
        val (sut, session) = makeSUT()
        session.stubbedResults = listOf(Result.success(HttpResponse(makeSearchResponseBody(), 200)))

        sut("bílý jogurt")

        assertEquals(
            "https://world.openfoodfacts.org/cgi/search.pl?search_terms=b%C3%ADl%C3%BD%20jogurt&json=1&page_size=20" +
                "&fields=code,product_name,product_name_cs,product_name_en,nutriments",
            session.lastRequest?.url,
        )
        assertEquals(10_000, session.lastRequest?.timeoutMillis)
    }

    @Test
    fun search_withNonSuccessStatusCode_throwsServerErrorInsteadOfDecodeFailure() = runTest {
        val (sut, session) = makeSUT()
        session.stubbedResults = List(Constants.OpenFoodFacts.MAX_ATTEMPTS) { Result.success(HttpResponse("Too Many Requests", 429)) }

        try {
            sut("mleko")
            fail("Expected SearchFoodExternallyError.ServerError to be thrown")
        } catch (error: SearchFoodExternallyError.ServerError) {
            assertEquals(429, error.statusCode)
        }
        assertEquals(Constants.OpenFoodFacts.MAX_ATTEMPTS, session.requestCount)
    }

    @Test
    fun search_withNotFoundStatusCode_throwsServerErrorWithoutRetrying() = runTest {
        val (sut, session) = makeSUT()
        session.stubbedResults = listOf(Result.success(HttpResponse("Not Found", 404)))

        try {
            sut("mleko")
            fail("Expected SearchFoodExternallyError.ServerError to be thrown")
        } catch (error: SearchFoodExternallyError.ServerError) {
            assertEquals(404, error.statusCode)
        }
        assertEquals("only 429 and 5xx are plausibly transient", 1, session.requestCount)
    }

    @Test
    fun search_withTransientServerErrorThenSuccess_retriesAndReturnsMappedItems() = runTest {
        val (sut, session) = makeSUT()
        session.stubbedResults = listOf(
            Result.success(HttpResponse("Service Unavailable", 503)),
            Result.success(HttpResponse(makeSearchResponseBody(), 200)),
        )

        val items = sut("mleko")

        assertEquals(1, items.size)
        assertEquals(2, session.requestCount)
    }

    @Test
    fun search_withTimedOutThenSuccess_retriesAndReturnsMappedItems() = runTest {
        val (sut, session) = makeSUT()
        session.stubbedResults = listOf(
            Result.failure(SocketTimeoutException()),
            Result.success(HttpResponse(makeSearchResponseBody(), 200)),
        )

        val items = sut("mleko")

        assertEquals(1, items.size)
        assertEquals(2, session.requestCount)
    }

    @Test
    fun search_withPersistentNetworkConnectionLost_throwsAfterMaxAttempts() = runTest {
        val (sut, session) = makeSUT()
        session.stubbedResults = List(Constants.OpenFoodFacts.MAX_ATTEMPTS) { Result.failure(SocketException("Connection reset")) }

        try {
            sut("mleko")
            fail("Expected SocketException to be thrown")
        } catch (error: SocketException) {
            assertTrue(error !is ConnectException)
        }
        assertEquals(Constants.OpenFoodFacts.MAX_ATTEMPTS, session.requestCount)
    }

    @Test
    fun search_withNotConnectedToInternet_throwsWithoutRetrying() = runTest {
        val (sut, session) = makeSUT()
        session.stubbedResults = listOf(Result.failure(UnknownHostException()))

        try {
            sut("mleko")
            fail("Expected UnknownHostException to be thrown")
        } catch (_: IOException) {
        }
        assertEquals(1, session.requestCount)
    }

    // MARK: - Helpers

    private fun makeSUT(): Pair<SearchFoodExternallyUseCase, HttpSessionFake> {
        val session = HttpSessionFake()
        return SearchFoodExternallyUseCase(session = session, retryDelay = Duration.ZERO) to session
    }

    private fun makeSearchResponseBody(): String =
        """
        {
            "count": 1,
            "products": [
                {
                    "code": "1234567890123",
                    "product_name_cs": "Mléko",
                    "nutriments": { "energy-kcal_100g": 60 }
                }
            ]
        }
        """.trimIndent()
}
