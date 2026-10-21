package antoni.kalorie.core.appcheck

import com.google.firebase.appcheck.AppCheckProviderFactory
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

fun createAppCheckProviderFactory(): AppCheckProviderFactory = DebugAppCheckProviderFactory.getInstance()
