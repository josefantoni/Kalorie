package antoni.kalorie.core.models

import java.time.Instant

data class FoodFrequencyEntry(
    val count: Int,
    val lastLoggedAt: Instant,
    val item: FoodItemDomain,
)
