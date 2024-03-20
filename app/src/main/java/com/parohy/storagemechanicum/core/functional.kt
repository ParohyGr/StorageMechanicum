package shared

import com.parohy.storagemechanicum.AppState
import zoom.*

/**
 * Return type for operations that can fail and we ratter want explicit value than exception.
 * Since version 1.3 Kotlin itself has [kotlin.Result] but it can't be parametrized in failure type.
 */
sealed interface Result<out E, out V>
data class Success<out A> (val value: A): Result<Nothing, A>
// also Failure (declared lower)

inline fun <E, V> Result<E, V>.onSuccess(f: (V) -> Unit) = apply { if(this is Success) f(value) }
inline fun <E, V> Result<E, V>.onFailure(f: (E) -> Unit) = apply { if(this is Failure) f(value) }

val <E, V> Result<E, V>?.successOrNull: V? get() = (this as? Success)?.value

fun <E, V> Result<E, V>.toState() : GrState<E, V> = fold(::Failure, ::Content)
fun <E, B> Result<E, B>.toUpdate(): Update<E>?    = fold(::Failure, { null })


/**
 * Helper block when you have to deal with multiple result values at once.
 * Instead of chaining with map/flatMap you can wrap it in [resultsScope] and
 * inside this block you can call [ResultsScope.success] extension on Result instance.
 * It reads value if it is Success or returns whole block with Failure.
 * This was one of major points when we realized that coroutines creates more problems than solutions.
 * Trying to implement this logic to work correctly with nested coroutines is grandiose shitshow.
 *
 * ```
 * val result = resultsScope {
 *  val result1 = someResult().success()  // if this is Success we continue on next line, if Failure we return it
 *  val result2 = otherResult().success()
 *  result1 + result2
 * }
 * ```
 */
//fun <E, V> resultsScope(@BuilderInference block: ResultsScope<E>.() -> V): Result<E, V> =
//    try {
//        Success(object : ResultsScope<E>{}.block())
//    } catch (e: ResultFailure) {
//        e.err as Failure<E>
//    }

//private class ResultFailure(val err: Any): RuntimeException()
//
//interface ResultsScope<E> {
//    fun <V> Result<E, V>.success(): V = when(this) {
//        is Success -> value
//        is Failure -> throw ResultFailure(this)
//    }
//}

/**
 * FlatMap in this type represents chaining of multiple operations that can fail without writing explicit when/if
 * ```
 * val result = sendRequest().flatMap { result -> anotherRequest().flatMap { otherResult -> thirdRequest() } }
 * ```
 */
inline fun <E, V, V2> Result<E, V>.flatMap(f: (V) -> Result<E, V2>): Result<E, V2> = when(this) {
    is Failure -> this
    is Success -> f(value)
}

//inline fun <E, V> Result<E, V>.flatMapError(f: (E) -> Result<E, V>): Result<E, V> = when(this) {
//    is Failure -> f(value)
//    is Success -> this
//}

inline fun <E, V, V2> Result<E, V>.map(f: (V) -> V2) : Result<E, V2> = when(this) {
    is Failure -> this
    is Success -> Success(f(value))
}

inline fun <E, V, E2> Result<E, V>.mapError(f: (E) -> E2) : Result<E2, V> = when(this) {
    is Failure -> Failure(f(value))
    is Success -> this
}

/**
/ Provide default "Success" value if current value is Failure
**/
inline fun <E, V> Result<E, V>.recover(f: (E) -> V): Result<E, V> = when(this) {
    is Failure -> Success(f(value))
    is Success -> this
}

inline fun <E, V, A> Result<E, V>.fold(failure: (E) -> A, success: (V) -> A): A = when(this) {
    is Failure -> failure(value)
    is Success -> success(value)
}

/**
 * State type for values that requires some form of fetching/loading
 * We used to called it just "State" but same name is used by Compose
 * [Content] case also contains information if there is some running update for current value
 */
sealed interface GrState<out E, out V>
object     Loading                                                                : GrState<Nothing, Nothing>, Update<Nothing>
data class Content<out V>(val value: V, val update: Update<ErrorWithMsg>? = null) : GrState<Nothing, V>
data class Failure<out E>(val value: E)                                           : GrState<E, Nothing>, Result<E, Nothing>, Update<E>

val <E, V> GrState<E, V>?.isLoading: Boolean get() = this is Loading
val <E, V> GrState<E, V>?.isFailure: Boolean get() = this is Failure
val <E, V> GrState<E, V>?.isContent: Boolean get() = this is Content

val <E, A> GrState<E, A>?.isUpdateRunning get() = (this as? Content<A>)?.update?.isRunning ?: false

val <E, A> GrState<E, A>?.isLoadingOrUpdate get() = this is Loading || (this as? Content<A>)?.update?.isRunning ?: false

val <E, V> GrState<E, V>?.contentOrNull: V? get() = (this as? Content)?.value
val <E, V> GrState<E, V>?.failureOrNull: E? get() = (this as? Failure)?.value

inline fun <E, V, A> GrState<E, V>?.ifContent(f: (V) -> A): A? = contentOrNull?.let(f)
inline fun <E, V, A> GrState<E, V>?.ifFailure(f: (E) -> A): A? = failureOrNull?.let(f)

inline fun <E, V, V2> GrState<E, V>.map(f: (V) -> V2): GrState<E, V2> = when(this) {
    is Content -> Content(f(value), update)
    is Loading -> this
    is Failure -> this
}

inline fun <E, V> GrState<E, V>.mapLoading(f: () -> V): V? = when(this) {
    is Content -> this.value
    is Loading -> f()
    is Failure -> null
}

inline fun <E, V, V2> GrState<E, V>.flatMap(f: (V) -> GrState<E,V2>?): GrState<E, V2>? = when(this) {
    is Content -> f(value)
    is Loading -> this
    is Failure -> this
}

/**
 * Update represents additional states for some value that can be refreshed/updated
 */
sealed interface Update<out E>
// Loading, Failure

val <A> Update<A>?.isRunning    get() = this == Loading
val <A> Update<A>?.isNotRunning get() = this != Loading
//fun <A> Update<A>?.ifFailure(action: (A) -> Unit) = if(this is Failure) action(this.value) else Unit




/**
 * Optics extension for Result and GrState sealed classes.
 * Helps to reference values "inside" some sealed class case
 */
val <A, B, C> Lens<A, Result<B, C>>.success : Optional<A, C> get() = compose(resultSuccess())
val <A, B, C> Lens<A, Result<B, C>>.failure : Optional<A, B> get() = compose(resultFailure())
val <A, B, C> Optional<A, Result<B, C>>.success : Optional<A, C> get() = compose(resultSuccess())
val <A, B, C> Optional<A, Result<B, C>>.failure : Optional<A, B> get() = compose(resultFailure())

/**
 * access for value inside GrState.Content
 */
val <A, B> Optional<A, GrState<ErrorWithMsg, B>>.content: Optional<A, B> get() = compose(content())

/**
 * access for whole GrState.Content state
 */
val <A, B, C> Optional<A, GrState<B, C>>.contentState: Optional<A, Content<C>> get() = compose(contentState())

private fun <F, S> resultSuccess() = Prism<Result<F, S>, S>(
    get = { a -> (a as? Success)?.value },
    set = ::Success)

private fun <F, S> resultFailure() = Prism<Result<F, S>, F>(
    get = { a -> (a as? Failure)?.value },
    set = ::Failure)

private fun <A> content() = object: Optional<GrState<ErrorWithMsg, A>, A> {
    override val get: (GrState<ErrorWithMsg, A>?) -> A? = { it.contentOrNull }
    override val set: (GrState<ErrorWithMsg, A>, A) -> GrState<ErrorWithMsg, A> = { state, value -> state.map { value } }
}

private fun <E, A> contentState() = Prism<GrState<E, A>, Content<A>>(
    get = { a -> (a as? Content) },
    set = { it })

infix fun <A> ((AppState) -> A).lens(setter: AppState.(A) -> AppState) = lens(getter = this, setter = setter)

infix fun <E, A> ((AppState) -> GrState<E,A>?).optional(setter: AppState.(GrState<E,A>) -> AppState) = optional(getter = this, setter = setter)


//fun <E, V> resultsScope(@BuilderInference block: ResultsScope<E>.() -> V): Result<E, V> =
//    try {
//        Success(object : ResultsScope<E>{}.block())
//    } catch (e: ResultFailure) {
//        e.err as Failure<E>
//    }
//
//private class ResultFailure(val err: Any): RuntimeException()
//
//interface ResultsScope<E> {
//    fun <V> Result<E, V>.success(): V = when(this) {
//        is Success -> value
//        is Failure -> throw ResultFailure(this)
//    }
//}