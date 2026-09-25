package antoni.kalorie.core.utils

import antoni.kalorie.core.networking.FirestoreDataProviderError
import com.google.firebase.firestore.FirebaseFirestoreException

val Throwable.isFirestoreUnreachable: Boolean
    get() = this is FirestoreDataProviderError.Unreachable

val Throwable.isFirestorePermissionDenied: Boolean
    get() = this is FirebaseFirestoreException && code == FirebaseFirestoreException.Code.PERMISSION_DENIED
