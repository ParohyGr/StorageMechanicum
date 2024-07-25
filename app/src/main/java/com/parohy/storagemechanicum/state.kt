package com.parohy.storagemechanicum

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
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
import java.io.FileOutputStream
import java.io.IOException

typealias MutableAppState = MutableAtom<AppState>

sealed interface Msg : BaseMsg {
  data object DownloadToLocal    : Msg
  data object DownloadToExternal : Msg
  data class DownloadToExternalSAF(val uri: Uri) : Msg
  data object DownloadImageToScopedStorage : Msg
  data object ReadFromLocal      : Msg
  data object ReadFromExternal   : Msg
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
//          context.downloadPdfFile_private("image_external_private.jpg", inS)
          context.downloadPdfFile_public("image_external_public.jpg", inS)
        }
      )
    }
    update { copy(downloadExternal = result.toState()) }
  }

  is Msg.DownloadToExternalSAF -> {
    update { copy(downloadExternal = Loading) }
    val result = worker {
      httpClient.download(
        request = httpGet("https://fossbytes.com/wp-content/uploads/2017/10/android-eats-apple.jpg".toHttpUrl()),
        consume = { inS: BufferedInputStream ->
//          context.downloadPdfFile_private("image_external_private.jpg", inS)
          val outS = context.contentResolver.openFileDescriptor(msg.uri, "w")?.fileDescriptor?.let(::FileOutputStream)
          outS?.write(inS.readBytes())
          outS?.close()

          msg.uri
        }
      )
    }
    update { copy(downloadExternal = result.toState()) }
  }

  is Msg.DownloadImageToScopedStorage -> {
    val result = worker {
      httpClient.download(
        request = httpGet("https://fossbytes.com/wp-content/uploads/2017/10/android-eats-apple.jpg".toHttpUrl()),
        consume = { inS: BufferedInputStream ->
          val imagesUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
          else
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI

          val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "image_scoped_storage.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
//                put(MediaStore.Images.Media.WIDTH, 0)
//                put(MediaStore.Images.Media.HEIGHT, 0)
          }

          try {
            context.contentResolver.insert(imagesUri, contentValues)?.also { uri ->
              context.contentResolver.openOutputStream(uri).use { outS ->
                outS?.write(inS.readBytes())
                outS?.close()
              }
            } ?: throw IOException("Couldn't create MediaStore entry")
          } catch (e: IOException) {
            e.printStackTrace()
            null
          }
        }
      )
    }
    update { copy(downloadExternal = result.toState()) }
  }

  else -> TODO("Baram buc")
}