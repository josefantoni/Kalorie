package antoni.kalorie

import android.app.Application
import antoni.kalorie.core.auth.CurrentActivityProvider

class KalorieApplication : Application() {

    // MARK: - Properties

    val currentActivityProvider = CurrentActivityProvider()

    // MARK: - Functions

    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(currentActivityProvider)
    }
}
