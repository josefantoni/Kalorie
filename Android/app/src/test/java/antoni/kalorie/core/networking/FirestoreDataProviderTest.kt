package antoni.kalorie.core.networking

import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerializationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.fail
import org.junit.Test

class FirestoreDataProviderTest {

    // MARK: - Tests

    @Test
    fun performFirestoreCall_whenUnavailable_throwsUnreachable() = runTest {
        try {
            performFirestoreCall<Unit> { throw FirebaseFirestoreException("offline", FirebaseFirestoreException.Code.UNAVAILABLE) }
            fail("Expected Unreachable")
        } catch (_: FirestoreDataProviderError.Unreachable) {
        }
    }

    @Test
    fun performFirestoreCall_whenPermissionDenied_rethrowsTheSameInstance() = runTest {
        val error = FirebaseFirestoreException("denied", FirebaseFirestoreException.Code.PERMISSION_DENIED)

        try {
            performFirestoreCall<Unit> { throw error }
            fail("Expected the permission-denied error")
        } catch (thrown: FirebaseFirestoreException) {
            assertSame(error, thrown)
        }
    }

    @Test
    fun performFirestoreCall_whenNotAFirestoreError_rethrowsTheSameInstance() = runTest {
        val error = SerializationException("bad payload")

        try {
            performFirestoreCall<Unit> { throw error }
            fail("Expected the serialization error")
        } catch (thrown: SerializationException) {
            assertSame(error, thrown)
        }
    }

    @Test
    fun performFirestoreCall_whenCancelled_rethrowsTheSameInstance() = runTest {
        val error = CancellationException("cancelled")

        try {
            performFirestoreCall<Unit> { throw error }
            fail("Expected the cancellation")
        } catch (thrown: CancellationException) {
            assertSame(error, thrown)
        }
    }

    @Test
    fun performFirestoreCall_onSuccess_returnsTheBlocksValue() = runTest {
        val result = performFirestoreCall { "value" }

        assertEquals("value", result)
    }
}
