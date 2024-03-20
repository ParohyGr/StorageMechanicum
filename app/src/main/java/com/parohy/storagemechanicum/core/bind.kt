package common

import android.view.View
import androidx.viewbinding.ViewBinding
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import com.parohy.storagemechanicum.AppState
import com.parohy.storagemechanicum.flow
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import shared.ErrorWithMsg
import shared.GrState

/**
 * Lifecycle-aware observation of some stream.
 * Automatically cancels itself when activity is destroyed or when view is detached/removed from activity.
 * In contrast with LiveData this is active even when activity is paused.
 * If you want to change this behavior you can simply do something like this:
 * ```
 * bind(data.filter { activity.isResumed }) { ... }
 * ```
 * WARNING: every call adds new observer. Don't call it multiple times because you register same observer many times.
 * Typical situation with this mistake is for example: onClick { bind(bla){} }
 * Another often mistake is nested call: bind(a) { bind(b) {} } this can by usually rewritten with [by] function
 *
 * WARNING2: don't bind inside RecyclerView items if you don't know what you are doing. Because of recycling it will be called many times
 */

fun <A> View.bind(flow: Flow<A>, action: (A) -> Unit) = attachedScope { flow.collect { action(it) } }

/**
 * Variant of [bind] that accepts any function that gets some value from AppState and internally converts it to stream.
 * This opens many possibilities how to use it:
 * ```
 * bind(S.something) { ... }
 * bind(AppState::something) { ... }
 * bind({ something }) { ... }
 * bind(select{ something }.filterNotNull()) { ... }
 * ```
 * Instead of combining two streams we can directly create stream with 2 (or more) values
 * ```
 * bind(latest(S.something, S.somethingElse)) { (a, b) -> }
 * bind({ something to somethingElse }) { (a, b) ->  }
 * ```
 */

fun <A> View.bind(getter: AppState.() -> A, action: (A) -> Unit) = bind(getter.flow, action)

/**
 *  Helper that visually simplifies call of "flatMapLatest". In Rx was the same operation called "switchMap".
 *  We have original stream and for each value we want to create new stream but also we want to unsubscribe previous one.
 *  In other words: we are observing some data and on every change we want to create subscription to some other data.
 *
 *  Example:
 *  We have some search/filter editText on UI. When user types we send this query to server and it returns list of results.
 *  But as user is typing we are sending multiple requests in parallel. We have no guaranty that the results will came in the same order.
 *  We always want only the latest one (because it corresponds to current value in editText) and we have to ignore all previous ones.
 *  Current text in editText is the "key" by which we select only the correct result. Changes of text are stream by which we are selecting stream of results.
 *  ```
 *  val query: Flow<String> = editText.observeTextChanges()
 *
 *  // wrong solution because nested bind creates many subscriptions:
 *  bind(query) { text -> bind(S.searchResults.at(text)) { result -> } }
 *
 *  // correct solution, no nesting:
 *  bind(S.searchResults by query) { (text, result) -> ... }
 *  ```
 *  [this] receiver is function that provides Map of States
 *  [keyFlow] is stream of keys. Every time this produces new key, it is send to receiver function and it selects appropriate value from Map
 */
infix fun <K, V> ((AppState) -> Map<K, GrState<ErrorWithMsg, V>>).by(keyFlow: Flow<K>): Flow<Pair<K, GrState<ErrorWithMsg, V>?>> =
    keyFlow.flatMapLatest { key -> this.flow.map { key to it[key] } }

/**
 *  Runs [action] in coroutine scope that automatically cancels when view is detached.
 *  It also handles opposite constraint, action is postponed until view is actually attached.
 *  When view will never be attached to activity the action is never called (no coroutine is created).
 *  It's a safety measure because until the onAttach is called we don't have guarantee that onDetach will be called and whole coroutine could leak.
 */
inline fun View.onAttachState(
    crossinline onAttach: () -> Unit,
    crossinline onDetach: () -> Unit) = addOnAttachStateChangeListener(object: View.OnAttachStateChangeListener {
    override fun onViewDetachedFromWindow(view: View) = onDetach()
    override fun onViewAttachedToWindow(view: View) = onAttach()
})
inline fun View.onAttach(crossinline action: () -> Unit) = onAttachState(action, {})
inline fun View.onDetach(crossinline action: () -> Unit) = onAttachState({}, action)
fun ViewBinding.attachedScope(action: suspend CoroutineScope.() -> Unit) = when {
    root.isAttachedToWindow -> root.scope.launch(block = action)
    else                    -> root.onAttach { root.scope.launch(block = action) }
}

fun View.attachedScope(action: suspend CoroutineScope.() -> Unit) = when {
    isAttachedToWindow -> scope.launch(block = action)
    else               -> onAttach { scope.launch(block = action) }
}

fun <A, B>    latest(x: Flow<A>, y: Flow<B>)                         = combine(x, y, ::Pair)
fun <A, B, C> latest(x: Flow<A>, y: Flow<B>, z: Flow<C>)             = combine(x, y, z, ::Triple)

fun <A, B>    latest(a: AppState.() -> A, b: AppState.() -> B)                      = latest(a.flow, b.flow)
fun <A, B, C> latest(a: AppState.() -> A, b: AppState.() -> B, c: AppState.() -> C) = latest(a.flow, b.flow, c.flow)
//fun <A, B, C, D> latest(a: AppState.() -> A, b: AppState.() -> B, c: AppState.() -> C, d: AppState.() -> D) = latest(a.flow, b.flow, c.flow, d.flow)

private val View.scope get() =
    CoroutineScope(Dispatchers.Main.immediate + crashLogger).apply { onDetach(::cancel) }

private val crashLogger = CoroutineExceptionHandler { _, err -> err.printStackTrace() }