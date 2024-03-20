package com.parohy.storagemechanicum

import android.content.Context
import android.net.Uri
import okhttp3.HttpUrl.Companion.toHttpUrl
import shared.ApiError
import shared.BaseMsg
import shared.GrState
import shared.Loading
import shared.MutableAtom
import shared.download
import shared.httpGet
import shared.toState
import shared.update
import shared.worker
import java.io.BufferedInputStream

typealias MutableAppState = MutableAtom<AppState>

sealed interface Msg : BaseMsg {
  data object DownloadToLocal    : Msg
  data object DownloadToExternal : Msg
}

data class AppState(
  val downloadLocal    : GrState<ApiError, Uri>? = null,
  val downloadExternal : GrState<ApiError, Uri>? = null,
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
  is Msg.DownloadToExternal -> {
    update { copy(downloadExternal = Loading) }
    val result = worker {
      httpClient.download(
        request = httpGet("https://fossbytes.com/wp-content/uploads/2017/10/android-eats-apple.jpg".toHttpUrl()),
        consume = { inS: BufferedInputStream ->
          context.downloadPdfFile_private("image_external_private.jpg", inS)
//          context.downloadPdfFile_public("image_external_public.jpg", inS)
        }
      )
    }
    update { copy(downloadExternal = result.toState()) }
  }
  else -> TODO("Baram buc")
}