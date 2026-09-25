package antoni.kalorie.core.networking

import antoni.kalorie.core.utils.Constants
import java.io.EOFException
import java.io.IOException
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.NoRouteToHostException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.URL
import kotlin.time.Duration
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

data class HttpRequest(
    val url: String,
    val userAgent: String,
    val timeoutMillis: Int,
)

data class HttpResponse(
    val body: String,
    val statusCode: Int,
)

interface HttpSessionProtocol {
    suspend fun data(request: HttpRequest): HttpResponse
}

class HttpURLConnectionSession : HttpSessionProtocol {

    // MARK: - Functions

    override suspend fun data(request: HttpRequest): HttpResponse = withContext(Dispatchers.IO) {
        val connection = URL(request.url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = request.timeoutMillis
            connection.readTimeout = request.timeoutMillis
            connection.setRequestProperty("User-Agent", request.userAgent)
            val statusCode = connection.responseCode
            val stream = if (statusCode in 200..299) connection.inputStream else connection.errorStream
            HttpResponse(body = stream?.bufferedReader()?.use { it.readText() }.orEmpty(), statusCode = statusCode)
        } finally {
            connection.disconnect()
        }
    }
}

object OpenFoodFactsTransientRequest {

    // MARK: - Functions

    suspend fun data(request: HttpRequest, session: HttpSessionProtocol, retryDelay: Duration): HttpResponse {
        var attempt = 1
        while (true) {
            try {
                val response = session.data(request)
                val isTransientFailure = response.statusCode == 429 || response.statusCode in 500..599
                if (!isTransientFailure || attempt >= Constants.OpenFoodFacts.MAX_ATTEMPTS) return response
            } catch (error: CancellationException) {
                throw error
            } catch (error: IOException) {
                if (!isTransient(error) || attempt >= Constants.OpenFoodFacts.MAX_ATTEMPTS) throw error
            }
            delay(retryDelay * attempt)
            attempt += 1
        }
    }

    private fun isTransient(error: IOException): Boolean = when (error) {
        is SocketTimeoutException, is EOFException -> true
        is ConnectException, is NoRouteToHostException -> false
        is SocketException -> true
        else -> false
    }
}
