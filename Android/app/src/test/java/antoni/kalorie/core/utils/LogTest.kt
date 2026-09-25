package antoni.kalorie.core.utils

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LogTest {

    // MARK: - Properties

    private val originalReporting = Log.errorReporting

    // MARK: - Tests

    @After
    fun tearDown() {
        Log.errorReporting = originalReporting
    }

    @Test
    fun error_recordsTheErrorWithTheReporter() {
        val recorded = mutableListOf<Throwable>()
        Log.errorReporting = ErrorReporting { recorded.add(it) }
        val failure = RuntimeException("boom")

        Log.error(failure, "test")

        assertEquals(listOf<Throwable>(failure), recorded)
    }

    @Test
    fun warning_isNotRecordedWithTheReporter() {
        val recorded = mutableListOf<Throwable>()
        Log.errorReporting = ErrorReporting { recorded.add(it) }

        Log.warning(RuntimeException("minor"), "test")

        assertTrue(recorded.isEmpty())
    }
}
