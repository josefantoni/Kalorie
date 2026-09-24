package antoni.kalorie.core.utils

import androidx.annotation.StringRes
import java.util.UUID

data class AlertItem(
    @StringRes val titleRes: Int,
    @StringRes val messageRes: Int? = null,
    val id: UUID = UUID.randomUUID(),
)
