package com.parohy.storagemechanicum

import android.content.Context
import android.net.Uri
import android.util.Log
import okhttp3.HttpUrl.Companion.toHttpUrl
import shared.ApiError
import shared.Atom
import shared.BaseMsg
import shared.ErrorWithMsg
import shared.GrState
import shared.Loading
import shared.MutableAtom
import shared.download
import shared.httpGet
import shared.load
import shared.toState
import shared.update
import shared.worker
import java.io.BufferedInputStream

typealias MutableAppState = MutableAtom<AppState>

sealed interface Msg : BaseMsg {
  data object DownloadToLocal    : Msg
  data object DownloadToExternal : Msg
  data object ReadFromLocal      : Msg
  data object ReadFromExternal   : Msg
}

data class AppState(
  val downloadLocal    : GrState<ApiError, Uri>? = null,
  val downloadExternal : GrState<ErrorWithMsg, Uri>? = null,
  val readLocal        : GrState<ErrorWithMsg, Uri>?  = null,
  val readExternal     : GrState<ErrorWithMsg, Uri>?  = null,
)

suspend fun MutableAppState.updateAppState(msg: BaseMsg, context: Context) = when (msg) {
  is Msg.DownloadToLocal -> {
    update { copy(downloadLocal = Loading) }
    val result = worker {
      httpClient.download(
        request = httpGet("https://fossbytes.com/wp-content/uploads/2017/10/android-eats-apple.jpg".toHttpUrl()),
        consume = { inS: BufferedInputStream ->
          context.cacheFile(inS, "image2.jpg")
        }
      )
    }
    update { copy(downloadLocal = result.toState()) }
  }
  is Msg.DownloadToExternal -> {}
  is Msg.ReadFromLocal -> {}
  is Msg.ReadFromExternal -> {}
  else -> TODO("Baram buc")
}