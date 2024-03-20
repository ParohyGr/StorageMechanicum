package shared

import android.net.Uri
import android.util.Log
import okhttp3.Headers
import okhttp3.Headers.Companion.headersOf
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import okhttp3.logging.HttpLoggingInterceptor.Level
import okio.BufferedSink
import java.io.BufferedInputStream
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

interface StatusCode {
  val code: Int
}

sealed interface ApiError {
  data class NoConnection (val message: String)                                                              : ApiError
  data class Timeout      (val message: String)                                                              : ApiError
  data class WithMessage  (val title: String? = null, val message: String, override val code: Int)               : ApiError, StatusCode
  data class IOException  (val url: String, override val code: Int, val headers: Headers, val problem: String) : ApiError, StatusCode
  data class ResolverError(val uri: Uri)                                                                     : ApiError
  data class ServiceError (val message: String)                                                                : ApiError

  fun is404(): Boolean = (this as? StatusCode)?.code == 404
  fun is401(): Boolean = (this as? StatusCode)?.code == 401
  fun is409(): Boolean = (this as? StatusCode)?.code == 409
  fun is429(): Boolean = (this as? StatusCode)?.code == 429
}

data class ErrorWithMsg(
  val msg  : BaseMsg,
  val error: ApiError)

fun OkHttpClient.Builder.logger(enabled: Boolean) =
  apply { if(enabled) addInterceptor(HttpLoggingInterceptor({ Log.i("🪃OkHttp", it) }).apply { level = Level.BODY }) }

inline fun jsonBody(crossinline block: JsonBuilder.() -> Unit) = object : RequestBody() {
  override fun contentType() = "application/json; charset=utf-8".toMediaTypeOrNull()
  override fun writeTo(sink: BufferedSink) = jsonBlock(block, sink::writeUtf8)
}

fun httpGet    (url: HttpUrl, headers: Headers = headersOf())                     = request(url, headers) { get() }
fun httpPost   (url: HttpUrl, body: RequestBody, headers: Headers = headersOf())  = request(url, headers) { post(body) }
fun httpPut    (url: HttpUrl, body: RequestBody, headers: Headers = headersOf())  = request(url, headers) { put(body) }
fun httpDelete (url: HttpUrl, headers: Headers = headersOf(), body: RequestBody?) = request(url, headers) { delete(body) }
fun httpPatch (url: HttpUrl, headers: Headers = headersOf(), body: RequestBody)   = request(url, headers) { patch(body) }

fun request(url: HttpUrl, headers: Headers = headersOf(), tag: String? = null, config: Request.Builder.() -> Request.Builder): Request =
  Request.Builder().url(url).headers(headers).tag(tag).config().build()

//fun OkHttpClient.cancelRequests(tag: String) {
//  dispatcher.queuedCalls().find  { it.request().tag() == tag }?.cancel()
//  dispatcher.runningCalls().find { it.request().tag() == tag }?.cancel()
//}

/**
 * Executes provided [Request] and if response is successful tries to parse returned body with [parse]
 * Only JSON responses are expected. If you need something else just make a modified copy of this function.
 * This should never throw any exception, fails are wrapped into [Result] [Failure]
 * This function is synchronous and it is expected from caller to be on some worker thread.
 */
fun <V> OkHttpClient.execute(request: Request, parse: (Json) -> V): Result<ApiError, V> =
  call(request).flatMap {
    it.use { response -> processResponse(request.url.encodedPath, response, parse) }
  }

private fun OkHttpClient.call(request: Request) =
  try {
    Success(newCall(request).execute())
  } catch (e: Exception) {
    Failure(when (e) {
      is SocketTimeoutException -> ApiError.Timeout(e.message ?: "")
      is UnknownHostException   -> ApiError.NoConnection(e.message ?: "")
      is ConnectException       -> ApiError.NoConnection(e.message ?: "")
      else -> {
        ApiError.IOException(request.url.encodedPath, 0, headersOf(), e.toString())
      }
    })
  }

fun OkHttpClient.download(request: Request, consume: (BufferedInputStream) -> Uri?): Result<ApiError, Uri> =
  call(request).map { response -> response.body!!.byteStream().let(::BufferedInputStream).use(consume)!! }

private fun <V> processResponse(url: String, response: Response, parse: (Json) -> V) : Result<ApiError, V> = try {
  val json = parseJsonTree(response.body!!.charStream())

  if (response.isSuccessful)
    Success(parse(json))
  else {
    Failure(
      ApiError.WithMessage(
        "DACO",
        "NEVIEM CO",
        response.code
      )
    )
  }
} catch (e: Exception) {
  Failure(ApiError.IOException(url, response.code, response.headers, e.toString()))
}
