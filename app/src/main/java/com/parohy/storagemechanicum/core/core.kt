package shared

import java.util.concurrent.Executor
import kotlin.coroutines.*

/**
 * Something like "launch" but without Job, structured concurrency and cancellation complexity.
 * This function exists only because standard [startCoroutine] is in Kotlin defined as an extension
 * on the suspend function and it looks confusing and not very readable when used in code.
 *
 * Expectation is that this function should never throw. All exceptions should come to [onFinish].
 * If we by chance find out in the future that this is not true then we should stop using suspend functions at all
 * because it is badly documented mess full of landmines that only tries to look that "simplifies something"
 *
 * Only thing that suspend functions provides is reduction of "callback hell" but it's
 * questionable if it is really such a huge problem.
 *
 * [context] standard CoroutineContext propagated into this coroutine
 * [block] suspend function that you want to execute
 * [onFinish] called at the end of whole coroutine. Here comes return value of [block] or exception if [block] thrown something
 */
fun <A> coroutine(context: CoroutineContext, block: suspend () -> A, onFinish: (kotlin.Result<A>) -> Unit) =
  block.startCoroutine(Continuation(context, onFinish))


/**
 * This is just a marker for functions that are expected to be called on worker thread.
 * It serves as a documentation but also makes the code a little more foolproof.
 * Sealed interface with single private implementation is just a trick to prohibit creation of instances anywhere else in the code.
 * Only way how get the instance is by calling [worker] function which also guaranties that you are really on some thread.
 *
 * This is maybe overkill and we can delete it in the future
 */
sealed interface Worker
private object RealWorker: Worker

@Deprecated(level = DeprecationLevel.ERROR, message = "Don't call this on worker thread! You should update AppState always on the same thread!")
fun <A> Worker.update(change: A.() -> A) {}

/**
 * Something like "withContext" but without Job, structured concurrency and cancellation complexity.
 * It executes provided function on worker thread and returns value to the caller's thread.
 * Thread pool [WorkerPool] has to be provided through [CoroutineContext].
 * It intentionally crashes otherwise, there is no default.
 *
 * [block] Lambda that you want to execute on the thread pool. It is intentionally not suspend! (you can't start nested Worker)
 * We almost never need nested coroutines, it is just a mess and most of android application doesn't have
 * such use cases at all. For example if you need to call API request, parse response and save it
 * into preferences just run single worker and do all these 3 operations sequentially and synchronously.
 * No need to crazily jump between multiple threads or coroutines.
 */
suspend fun <A> worker(block: Worker.() -> A): A {
  val dispatch = coroutineContext[ContinuationInterceptor]!! as Dispatcher
  return suspendCoroutine { cont ->
    dispatch { cont.resumeWith(runCatching { RealWorker.block() }) }
  }
}

class Dispatcher(private val main: Executor, private val worker: Executor) : ContinuationInterceptor {
  override val key = ContinuationInterceptor.Key // shit api
  operator fun invoke(block: () -> Unit) = worker.execute(block)
  override fun <T> interceptContinuation(continuation: Continuation<T>) =
    Continuation(continuation.context) {
      main.execute { continuation.resumeWith(it) }
    }
}

/**
 * Marker interface for all messages.
 * It's recommended to create sealed class/interface with application specific messages
 */
interface BaseMsg

/**
 * Helper message for [loadPage] logic. It's automatically created and handles loading of next page
 *
 * WARNING: Don't use this for any other purpose if you are not sure how the whole core logic works.
 * Default behaviour is that all Messages are just "requests" and logic is encapsulated inside "update"
 * function. [RunOnStateThread] is exact opposite, it is logic send from the outside.
 * Core only executes it on the same thread as all other messages. All changes to AppState are
 * executed sequentially on this one thread.
 */
class RunOnStateThread(val action: suspend (RunOnStateThread) -> Unit): BaseMsg

/**
 *  Wrapper around message that's inserted into CoroutineContext. Every suspend function
 *  can retrieve it and find out witch message started this coroutine.
 *  This message is also inserted into [ErrorWithMsg] when http fails. UI code then can implement "retry" just by sending same message one more time.
 *
 *  WARNING: this way of retrying isn't always safe. It depends on actual logic of that message.
 *  If that message runs multiple side effects you retry all of them and sometimes you don't want that.
 */
data class CoroutineMsg(val msg: BaseMsg): AbstractCoroutineContextElement(CoroutineMsg) {
  companion object Key: CoroutineContext.Key<CoroutineMsg>
}