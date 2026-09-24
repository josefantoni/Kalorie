package antoni.kalorie.core.utils

import antoni.kalorie.core.networking.FirestoreDataProviderError

val Throwable.isFirestoreUnreachable: Boolean
    get() = this is FirestoreDataProviderError.Unreachable
