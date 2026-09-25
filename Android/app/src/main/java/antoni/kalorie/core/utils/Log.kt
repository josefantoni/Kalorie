package antoni.kalorie.core.utils

import android.util.Log as AndroidLog

fun interface ErrorReporting {
    fun record(error: Throwable)
}

object Log {

    // MARK: - Properties

    @Volatile
    var errorReporting: ErrorReporting = ErrorReporting { }

    // MARK: - Functions

    fun warning(error: Throwable, category: String = "app") {
        AndroidLog.w(category, error.toString())
    }

    fun error(error: Throwable, category: String = "app") {
        AndroidLog.e(category, error.toString())
        errorReporting.record(error)
    }
}
