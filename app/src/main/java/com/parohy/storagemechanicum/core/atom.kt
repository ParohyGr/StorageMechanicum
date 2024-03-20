package shared

import androidx.compose.runtime.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import zoom.Setter
import zoom.rem
import java.util.concurrent.CopyOnWriteArraySet

/**
 * Atom is thread safe, observable, mutable wrapper for immutable value.
 *
 * It's similar to [java.util.concurrent.atomic.AtomicReference] or kotlinx.atomicfu but these libraries doesn't have API we need.
 * Atom always has to contain only immutable values to work correctly.
 * State change is always done atomically by copying current value in [update].
 *
 * Atom should be used as a basic data holder instead of LiveData, BehaviorSubject, SharedFlow, etc.
 * It doesn't represents stream itself but can be easily converted to one. (wrap [watch] in Observable.create{} or callbackFlow{})
 *
 * We created Atom because we need specified properties and a none of previously mentioned libraries has all of them at once:
 * - Atom can't be uninitialized (initial value must be set in constructor)
 * - we need synchronous, thread-safe read of current value
 * - ability to register change observers
 * - registered observer immediately called with current value
 * - atomic update operation consisting of "read current + modify + write"
 * - "map" operation that returns value that preserves all previous properties
 * - separated Read-only interface [Atom] vs [MutableAtom]
 * - as a bonus we now have zero dependencies on any library
 *
 * Any number of observers can be registered by calling [watch]. Warning: Observers can be called on different threads.
 * They are always called on the same thread that the [update] was called from, there is no dispatching.
 * New observer is immediately invoked with current value upon registration.
 *
 * Current implementation is not optimized for maximum performance (there is a lot of locking and a lot of code inside locked sections).
 * Instead, it's written without advanced patterns to be easily readable.
 * Possible improvements (if you ever need them):
 * - optimistic algorithm running in loop with retries (look at swap method in Closure's Atom)
 * - calling of watchers can be extracted outside of synchronized block
 * - keeping calculated hashCode of current value as a field (value is expected to be immutable, we can memoize hashCode)
 *
 * As a concept it's inspired mostly by Clojure's Atom
 * @see <a href="https://clojure.org/reference/atoms">Clojure docs</a>
 * @see <a href="https://github.com/clojure/clojure/blob/master/src/jvm/clojure/lang/Atom.java">Clojure's Atom source</a>
 *
 * Arrow library also has something similar (it is using suspend functions instead of locks)
 * @see <a href="https://github.com/arrow-kt/arrow-fx/blob/master/arrow-fx-coroutines/src/main/kotlin/arrow/fx/coroutines/Atomic.kt">Arrow's Atomic source</a>
 */
interface Atom<A> {
  /**
   * Blocking read of current value. It's thread-safe but it can slow if currently runs some write
   */
  fun value(): A

  /**
   * Register new listener. It will be immediately called (on the same thread you called [watch] from) with value.
   * All other invocation can be called on different thread. (on the thread that the [MutableAtom.update] was called from)
   */
  fun watch(listener: (A) -> Unit): () -> Unit
}

interface MutableAtom<A>: Atom<A> {
  fun update(change: A.() -> A, onlyIf: A.() -> Boolean)
}

class AtomicCache<A>(init: A): MutableAtom<A> {
  private var value: A = init
  private val watchers = CopyOnWriteArraySet<((A) -> Unit)>()
  private val lock     = Object()

  override fun update(change: A.() -> A, onlyIf: A.() -> Boolean) {
    synchronized(lock) {
      if(onlyIf(value)) {
        val newValue = change(value)

        if(newValue != value) {
          value = newValue
          watchers.forEach { watcher ->
            try {
              watcher(value)
            } catch(t: Throwable) {
              t.printStackTrace()
            }
          }
        }
      }
    }
  }

  override fun watch(listener: (A) -> Unit): () -> Unit {
    synchronized(lock) {
      watchers.add(listener)
      listener(value)
    }

    return { synchronized(lock) { watchers.remove(listener) } }
  }

  override fun value(): A = synchronized(lock) { value }
}

/**
 * Creates a new read-only Atom with value derived from original Atom.
 * We are currently not caching this derived value and it is calculated in every
 * invocation of [Atom.value] because most of the time we are observing and not querying.
 */
fun <A, B> Atom<A>.map(transform: (A) -> B) = object: Atom<B> {

  override fun value(): B =
    transform(this@map.value())

  override fun watch(listener: (B) -> Unit): () -> Unit {
    var previousHash: Int? = null

    return this@map.watch {
      val value = transform(it)
      val currentHash = value.hashCode()
      if(previousHash != currentHash) {
        previousHash = currentHash
        listener(value)
      }
    }
  }
}

/**
 * Helper function for updating some inner value inside AppState
 *
 * @param setter It's expected to send here copy function that creates new AppState and changes specific value
 * @param value  Value we want to set somewhere inside AppState
 * @param onlyIf Optional predicate that checks current value and performs update only if true
 */
fun <S, V> MutableAtom<S>.update(
  setter : Setter<S, V>,
  value  : V,
  onlyIf : S.() -> Boolean = { true }) = update(setter %value, onlyIf)

fun <A> MutableAtom<A>.update(change: A.() -> A) =
  update(change, onlyIf = { true })

/**
 * Converts atom updates to observable stream of values.
 * WARNING: Only first observer will get "cached" value.
 * All other observers will get only real-time changes
 */
val <A> Atom<A>.flow: Flow<A>
  get() = callbackFlow {
    val unsubscribe = watch(::trySend)
    awaitClose { unsubscribe() }
  }