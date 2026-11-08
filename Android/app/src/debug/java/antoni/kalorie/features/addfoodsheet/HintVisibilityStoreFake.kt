package antoni.kalorie.features.addfoodsheet

import java.time.Instant

class HintVisibilityStoreFake(override var hiddenAt: Instant? = null, override var hideCount: Int = 0) : HintVisibilityStoreProtocol
