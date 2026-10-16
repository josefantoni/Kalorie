package antoni.kalorie.core.tips

import android.content.Context
import antoni.kalorie.core.auth.CurrentActivityProvider

fun createTipStore(context: Context, currentActivityProvider: CurrentActivityProvider): TipStoreProtocol = PlayBillingTipStore(context, currentActivityProvider)
