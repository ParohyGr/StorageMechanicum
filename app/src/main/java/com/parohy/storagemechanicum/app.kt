package com.parohy.storagemechanicum

import android.app.Application
import android.content.ContentResolver
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import shared.Atom
import shared.AtomicCache
import shared.BaseMsg
import shared.CoroutineMsg
import shared.Dispatcher
import shared.RunOnStateThread
import shared.coroutine
import shared.flow
import shared.logger
import shared.map
import shared.update
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

val gson: Gson = GsonBuilder().create()

/**
 * Queue into which we can send [BaseMsg] from any place in the app.
 * Messages are "requests" to some global state change.
 * App should never spam many messages in short time. We use smaller queue in debug build to detect these problems
 */
private val stateThread     = ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, ArrayBlockingQueue(20))
private val dispatchers     = Dispatcher(stateThread, Executors.newCachedThreadPool())
private val privateAppState = AtomicCache(AppState())
val state : Atom<AppState>  = privateAppState // Globally accessible read-only state

val httpClient = OkHttpClient.Builder()
  .logger(true)
  .build()



val <B> ((AppState) -> B).flow: Flow<B> get() = privateAppState.map { this(it) }.flow.distinctUntilChanged()

private lateinit var appContext : Context

fun send(msg: BaseMsg) {
  try {
    stateThread.execute {
      when(msg) {
        is RunOnStateThread -> coroutine(
          context  = dispatchers + CoroutineMsg(msg),
          block    = { msg.action(msg) },
          onFinish = { result -> result.onFailure { Log.e("send", it.message ?: "DACO") } })
        else -> coroutine(
          context  = dispatchers + CoroutineMsg(msg),
          block    = { privateAppState.updateAppState(msg, appContext) },
          onFinish = { result -> result.onFailure { Log.e("send", it.message ?: "DACO") } })
      }
    }
  } catch (e: RejectedExecutionException) {
    e.printStackTrace()
  }
}

class App: Application() {
  override fun onCreate() {
    super.onCreate()

    appContext = this

    privateAppState.update { AppState() }
  }
}