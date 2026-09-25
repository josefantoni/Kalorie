package antoni.kalorie.core.usecases

import antoni.kalorie.core.models.FoodItemDomain
import antoni.kalorie.core.networking.HttpRequest
import antoni.kalorie.core.networking.HttpSessionProtocol
import antoni.kalorie.core.networking.HttpURLConnectionSession
import antoni.kalorie.core.networking.OpenFoodFactsBarcodeResponseDTO
import antoni.kalorie.core.networking.OpenFoodFactsTransientRequest
import antoni.kalorie.core.utils.Constants
import java.net.URLEncoder
import kotlin.time.Duration
import kotlinx.serialization.json.Json

sealed class FetchFoodByBarcodeExternallyError : Exception() {
    data object InvalidURL : FetchFoodByBarcodeExternallyError()
    data class ServerError(val statusCode: Int) : FetchFoodByBarcodeExternallyError()
}

interface FetchFoodByBarcodeExternallyUseCaseProtocol {
    suspend operator fun invoke(barcode: String): FoodItemDomain?
}

class FetchFoodByBarcodeExternallyUseCase(
    private val session: HttpSessionProtocol = HttpURLConnectionSession(),
    private val retryDelay: Duration = Constants.OpenFoodFacts.RETRY_DELAY,
) : FetchFoodByBarcodeExternallyUseCaseProtocol {

    // MARK: - Functions

    override suspend fun invoke(barcode: String): FoodItemDomain? {
        if (barcode.isEmpty()) return null
        val encodedBarcode = URLEncoder.encode(barcode, Charsets.UTF_8).replace("+", "%20")
        val request = HttpRequest(
            url = "https://${Constants.OpenFoodFacts.HOST}/api/v2/product/$encodedBarcode?fields=$FIELDS",
            userAgent = Constants.OpenFoodFacts.USER_AGENT,
            timeoutMillis = Constants.OpenFoodFacts.REQUEST_TIMEOUT_MILLIS,
        )
        val response = OpenFoodFactsTransientRequest.data(request, session, retryDelay)
        if (response.statusCode !in 200..299) throw FetchFoodByBarcodeExternallyError.ServerError(response.statusCode)
        val decoded = json.decodeFromString<OpenFoodFactsBarcodeResponseDTO>(response.body)
        val product = decoded.product
        if (decoded.status != 1 || product == null) return null
        return product.asDomain()
    }

    private companion object {
        const val FIELDS = "code,product_name,product_name_cs,product_name_en,nutriments"
        val json = Json { ignoreUnknownKeys = true }
    }
}
