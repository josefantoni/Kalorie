package antoni.kalorie.core.usecases

import antoni.kalorie.core.models.FoodItemDomain
import antoni.kalorie.core.networking.HttpRequest
import antoni.kalorie.core.networking.HttpSessionProtocol
import antoni.kalorie.core.networking.HttpURLConnectionSession
import antoni.kalorie.core.networking.OpenFoodFactsResponseDTO
import antoni.kalorie.core.networking.OpenFoodFactsTransientRequest
import antoni.kalorie.core.utils.Constants
import java.net.URLEncoder
import kotlin.time.Duration
import kotlinx.serialization.json.Json

sealed class SearchFoodExternallyError : Exception() {
    data object InvalidURL : SearchFoodExternallyError()
    data class ServerError(val statusCode: Int) : SearchFoodExternallyError()
}

interface SearchFoodExternallyUseCaseProtocol {
    suspend operator fun invoke(query: String): List<FoodItemDomain>
}

class SearchFoodExternallyUseCase(
    private val session: HttpSessionProtocol = HttpURLConnectionSession(),
    private val retryDelay: Duration = Constants.OpenFoodFacts.RETRY_DELAY,
) : SearchFoodExternallyUseCaseProtocol {

    // MARK: - Functions

    override suspend fun invoke(query: String): List<FoodItemDomain> {
        val url = "https://${Constants.OpenFoodFacts.HOST}/cgi/search.pl" +
            "?search_terms=${URLEncoder.encode(query, Charsets.UTF_8).replace("+", "%20")}" +
            "&json=1&page_size=20&fields=$FIELDS"
        val request = HttpRequest(
            url = url,
            userAgent = Constants.OpenFoodFacts.USER_AGENT,
            timeoutMillis = Constants.OpenFoodFacts.REQUEST_TIMEOUT_MILLIS,
        )
        val response = OpenFoodFactsTransientRequest.data(request, session, retryDelay)
        if (response.statusCode !in 200..299) throw SearchFoodExternallyError.ServerError(response.statusCode)
        val decoded = json.decodeFromString<OpenFoodFactsResponseDTO>(response.body)
        return decoded.products.mapNotNull { it.asDomain() }
    }

    private companion object {
        const val FIELDS = "code,product_name,product_name_cs,product_name_en,nutriments"
        val json = Json { ignoreUnknownKeys = true }
    }
}
