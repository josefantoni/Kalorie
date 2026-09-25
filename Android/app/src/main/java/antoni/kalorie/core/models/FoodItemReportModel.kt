package antoni.kalorie.core.models

import java.time.Instant

data class FoodItemReportDomain(
    val barcode: String,
    val reportedBy: String,
    val reason: String,
    val reportedAt: Instant,
) {

    // MARK: - Functions

    companion object {
        fun id(barcode: String, userId: String): String = "${barcode}_$userId"
    }
}

sealed class FoodItemReportError : Exception() {
    data object ReasonRequired : FoodItemReportError()
    data object ReasonTooLong : FoodItemReportError()
}
