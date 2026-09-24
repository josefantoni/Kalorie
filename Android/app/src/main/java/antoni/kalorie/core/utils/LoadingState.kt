package antoni.kalorie.core.utils

sealed interface LoadingState<out T> {
    data object Idle : LoadingState<Nothing>
    data object Loading : LoadingState<Nothing>
    data class Loaded<T>(val value: T) : LoadingState<T>
    data class Error(val error: Throwable?) : LoadingState<Nothing>

    companion object {
        val loaded: LoadingState<Unit> = Loaded(Unit)
    }
}

val LoadingState<*>.isLoading: Boolean
    get() = this is LoadingState.Loading
