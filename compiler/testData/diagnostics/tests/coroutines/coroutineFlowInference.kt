// FIR_IDENTICAL
interface Flow<out F>

fun interface FlowCollector<in C> {
    abstract suspend fun emit(value: C)
}

inline fun <T, R> Flow<T>.transform(crossinline transform: suspend FlowCollector<R>.(T) -> Unit): Flow<R> {
    return null!!
}

fun <O> flowOf(vararg values: O): Flow<O> {
    return null!!
}

fun f() {
    fun <E> doEmit(collector: FlowCollector<E>) {}
    flowOf(1).transform { doEmit(this) }
}
