package com.parohy.storagemechanicum.core

import com.parohy.storagemechanicum.MutableAppState
import com.parohy.storagemechanicum.httpClient
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import shared.*

fun <V> Worker.get(client: OkHttpClient = httpClient, path: HttpUrl, headers: Headers = Headers.headersOf(), parse: (Json) -> V) =
  client.execute(httpGet(path, headers), parse)

fun <V> Worker.put(client: OkHttpClient = httpClient, path: HttpUrl, body: RequestBody, headers: Headers = Headers.headersOf(), parse: (Json) -> V) =
  client.execute(httpPut(path, body, headers), parse)

fun Worker.put(client: OkHttpClient = httpClient, path: HttpUrl, body: RequestBody, headers: Headers = Headers.headersOf()) =
  client.execute(httpPut(path, body, headers), parse = {})

fun Worker.put(client: OkHttpClient = httpClient, path: HttpUrl, headers: Headers = Headers.headersOf()) =
  client.execute(httpPut(path, jsonBody {}, headers), parse = {})

fun <V> Worker.post(client: OkHttpClient = httpClient, path: HttpUrl, body: RequestBody = jsonBody {}, headers: Headers = Headers.headersOf(), parse: (Json) -> V) =
  client.execute(httpPost(path, body, headers), parse)

fun Worker.post(client: OkHttpClient = httpClient, path: HttpUrl, body: RequestBody = jsonBody {}, headers: Headers = Headers.headersOf()) =
  client.execute(httpPost(path, body, headers), parse = {})

fun <V> Worker.delete(client: OkHttpClient = httpClient, path: HttpUrl, headers: Headers = Headers.headersOf(), body : RequestBody? = null, parse: (Json) -> V) =
  client.execute(httpDelete(path, headers, body), parse)

fun Worker.delete(client: OkHttpClient = httpClient, path: HttpUrl, headers: Headers = Headers.headersOf(), body : RequestBody? = null) =
  client.execute(httpDelete(path, headers, body), parse = {})

fun Worker.patch(client: OkHttpClient = httpClient, path: HttpUrl, body: RequestBody = jsonBody {}, headers: Headers = Headers.headersOf()) =
  client.execute(httpPatch(path, headers, body), parse = {})

fun <V> Worker.patch(client: OkHttpClient = httpClient, path: HttpUrl, body: RequestBody = jsonBody {}, headers: Headers = Headers.headersOf(), parse: (Json) -> V) =
  client.execute(httpPatch(path, headers, body), parse = parse)
