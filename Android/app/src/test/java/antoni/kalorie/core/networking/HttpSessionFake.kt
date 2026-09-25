package antoni.kalorie.core.networking

import java.io.IOException

class HttpSessionFake : HttpSessionProtocol {

    // MARK: - Properties

    var stubbedResults: List<Result<HttpResponse>> = emptyList()
    var requests: List<HttpRequest> = emptyList()
        private set

    val requestCount: Int
        get() = requests.size

    val lastRequest: HttpRequest?
        get() = requests.lastOrNull()

    // MARK: - Functions

    override suspend fun data(request: HttpRequest): HttpResponse {
        requests = requests + request
        val result = stubbedResults.getOrNull(requests.size - 1) ?: throw IOException("No stubbed result for request ${requests.size}")
        return result.getOrThrow()
    }
}
