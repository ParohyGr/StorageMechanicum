package shared

import androidx.compose.runtime.MutableState
import com.parohy.storagemechanicum.AppState
import com.parohy.storagemechanicum.MutableAppState
import zoom.Lens
import zoom.Optional
import zoom.modify
import kotlin.coroutines.coroutineContext

/**
 * Reference to some optional state inside AppState.
 * Helper alias for most often used case. Most of the things that we want to load are nullable "States" nested somewhere inside AppState
 */
typealias StateRef<A> = Optional<AppState, GrState<ErrorWithMsg, A>>
typealias BoolRef = Lens<AppState, Boolean>

/**
 * Basic helper function for most of the use cases.
 * When current state is failed or we don't have nothing it starts loading.
 * If we already have content it instead runs update of this data.
 *
 * Before emitting result it checks if we are still in loading state because state
 * could have been changed in the meantime and in this case we ratter discard result.
 *
 * @param ref It's reference to some immutable data inside AppState
 * @param asyncResult Some action that retrieves data. Usually http request.
 */
suspend fun <A> MutableAppState.loadOrUpdate(ref: StateRef<A>, asyncResult:  Worker.() -> Result<ApiError, A>) {
  val msg = coroutineContext[CoroutineMsg]!!.msg
  when (val state = ref.get(value())) {

    is Failure, null -> {
      update(ref, Loading)
      val result = worker { asyncResult().mapError { ErrorWithMsg(msg, it) }.toState() }
      update(ref, result, onlyIf = ref.isInLoadingState)
    }

    is Content -> {
      if (state.update !is Loading) {
        update(ref.updateRunning())
        val result = worker { asyncResult().mapError { ErrorWithMsg(msg, it) } }
        update(ref.updateResult(result), onlyIf = ref.isInUpdateState)
      }
    }

    is Loading -> { /*do nothing*/ }
  }
}

suspend fun <A> MutableAppState.load(ref: StateRef<A>, asyncResult: Worker.() -> Result<ApiError, A>) {
  val msg = coroutineContext[CoroutineMsg]!!.msg
  val current = ref.get(value())
  if(current !is Loading) {
    update(ref, Loading)
    val result = worker { asyncResult().mapError { ErrorWithMsg(msg, it) }.toState() }
    update(ref, result, onlyIf = ref.isInLoadingState)
  }
}

suspend fun MutableAppState.load(ref: BoolRef, asyncResult: Worker.() -> Unit) {
  val current = ref.get(value())
  if(!current) {
    update(ref, true)
    worker { asyncResult() }
    update(ref, false)
  }
}

suspend fun <T> MutableState<GrState<ErrorWithMsg, T>?>.loadOrUpdate(asyncResult: Worker.() -> Result<ApiError, T>) {
  val msg = coroutineContext[CoroutineMsg]!!.msg
  when (val state = value) {

    is Failure, null -> {
      value = Loading
      val result = worker { asyncResult().mapError { ErrorWithMsg(msg, it) }.toState() }
      if (value is Loading)
        value = result
    }

    is Content -> {
      if (state.update !is Loading) {
        value = state.copy(update = Loading)
        val result = worker { asyncResult().mapError { ErrorWithMsg(msg, it) } }
        if (value.isUpdateRunning)
          value = state.copy(update = result.toUpdate(), value = result.fold(failure = { state.value }, success = { it }))
      }
    }

    is Loading -> { /*do nothing*/ }
  }
}

suspend fun <T> MutableState<GrState<ErrorWithMsg, T>?>.load(asyncResult: Worker.() -> Result<ApiError, T>) {
  val msg = coroutineContext[CoroutineMsg]!!.msg
  when (value) {
    is Loading -> { /*do nothing*/ }
    else -> {
      value = Loading
      val result = worker { asyncResult().mapError { ErrorWithMsg(msg, it) }.toState() }
      if (value is Loading)
        value = result
    }
  }
}

//suspend fun <A> MutableAppState.load(key: RunningAction, asyncResult: Worker.() -> Result<ApiError, A>) {
//  val current = value().actions
//  if(key !in current) {
//    update { copy(actions = actions + key) }
//    worker { asyncResult() }
//    update { copy(actions = actions - key) }
//  }
//}

private val <A> StateRef<A>.isInLoadingState get() = { state: AppState -> get(state).isLoading }
private val <A> StateRef<A>.isInUpdateState  get() = { state: AppState -> contentState.get(state)?.update is Loading }

fun <A> StateRef<A>.updateRunning() =
  contentState.modify { copy(update = Loading) }

fun <A> StateRef<A>.updateResult(result: Result<ErrorWithMsg, A>) =
  contentState.modify { copy(
    update = result.toUpdate(),
    value  = result.fold(failure = { value }, success = { it })) }