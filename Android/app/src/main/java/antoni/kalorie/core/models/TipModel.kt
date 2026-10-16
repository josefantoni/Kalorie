package antoni.kalorie.core.models

data class TipProductDomain(val id: String, val displayPrice: String)

enum class TipPurchaseOutcome {
    PURCHASED,
    CANCELLED,
    PENDING,
}
