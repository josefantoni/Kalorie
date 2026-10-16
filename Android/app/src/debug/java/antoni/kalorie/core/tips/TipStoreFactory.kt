package antoni.kalorie.core.tips

import android.content.Context
import antoni.kalorie.core.auth.CurrentActivityProvider

private const val USE_FAKE_STORE = true

fun createTipStore(context: Context, currentActivityProvider: CurrentActivityProvider): TipStoreProtocol = if (USE_FAKE_STORE) TipStoreFake() else PlayBillingTipStore(context, currentActivityProvider)
