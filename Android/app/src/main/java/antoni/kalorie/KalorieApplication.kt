package antoni.kalorie

import android.app.Application
import antoni.kalorie.core.auth.CurrentActivityProvider
import antoni.kalorie.core.utils.ErrorReporting
import antoni.kalorie.core.utils.Log
import com.google.firebase.crashlytics.FirebaseCrashlytics

class KalorieApplication : Application() {

    // MARK: - Properties

    val currentActivityProvider = CurrentActivityProvider()

    // MARK: - Functions

    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(currentActivityProvider)
        val crashlytics = FirebaseCrashlytics.getInstance()
        crashlytics.isCrashlyticsCollectionEnabled = !BuildConfig.DEBUG
        Log.errorReporting = ErrorReporting { error -> crashlytics.recordException(error) }
    }
}
