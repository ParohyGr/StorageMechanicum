package com.parohy.storagemechanicum

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.content.FileProvider
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream

/*
* Write input stream to a file in the cache directory and return the file's URI
* In order to be able to share files Uri, we need to use FileProvider
* Most basic usage would be
* <paths>
*   <cache-path name="cache" path="." />
* </paths>
* This allows FileProvider to access the **cache** directory and all its content and subdirectories
* If you want to restrict it to a specific directory, you can use
* <paths>
*   <cache-path name="cache" path="./tmp" />
* </paths>
* This would allow FileProvider to access only the **tmp** directory in the cache directory
*
* name:
*   The name used to reference the path.
*   It's used when you create the Uri to share the file.
*   It's important to use a unique name to avoid conflicts with other paths.
* path:
*   Specifies the actual directory path relative to the specified storage location.
*   You can use this attribute to define subdirectories within the specified storage location.
*
* WARNING: Having '/' in file name will cause failure when trying to access the file!
*
* Similar setup applies to other directories like files, external, etc.
* */
fun Context.cacheFile(inputStream: BufferedInputStream, name: String, directoryName: String? = null): Uri {
  val path = if (directoryName != null) {
    val dir = File(cacheDir, directoryName)
    if (!dir.exists()) dir.mkdirs()
    dir.path
  } else
    cacheDir.path

  val file = File(path, name.replace("/", "_"))
  val outS = FileOutputStream(file)

  outS.use { stream ->
    val buffer = ByteArray(1024)
    inputStream.read(buffer)
    do {
      stream.write(buffer)
    } while (inputStream.read(buffer) != -1)
  }

  Uri.fromFile(file).also { Log.e("cacheFile", it.toString()) } // stary sposob. Na novsich android to uz nefunguje kvoli "bezpecnosti"
  return FileProvider.getUriForFile(this, "com.parohy.storagemechanicum.fileprovider", file).also { Log.e("cacheFile", it.toString()) }
}

//fun moveFileToDownloads(context: Context, from: File) {
//  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
//    context.contentResolver.downloadPdfFileSDK29(from.name, BufferedInputStream(from.inputStream()))
//  } else
//    context.downloadPdfFile_public(from.name, BufferedInputStream(from.inputStream()))
//}

/*Download file to external location*/
@RequiresApi(Build.VERSION_CODES.Q)
fun ContentResolver.downloadPdfFileSDK29(name: String, inputStream: BufferedInputStream) {
  val values = ContentValues().apply {
    put(MediaStore.Downloads.DISPLAY_NAME, name)
    put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
  }

  val downloadCollection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL)
  val uri = insert(downloadCollection, values)!!

  val buffer = ByteArray(1024)
  openOutputStream(uri, "w")?.use { stream ->
    inputStream.read(buffer)
    do {
      stream.write(buffer)
    } while (inputStream.read(buffer) != -1)
  }
  update(uri, values, null, null)
  values.clear()
}

/*
* Pristupujem k verejnému externemu úložisku
* Tu mozu citat subory aj ine aplikacie
* */
fun Context.downloadPdfFile_public(name: String, inputStream: BufferedInputStream): Uri {
  val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
  val document = File(downloads, name)

  if (!document.exists())
    document.createNewFile()

  val buffer = ByteArray(1024)
  document.outputStream().use { stream ->
    inputStream.read(buffer)
    do {
      stream.write(buffer)
    } while (inputStream.read(buffer) != -1)
  }

  Uri.fromFile(document).also { Log.e("downloadPdfFile", it.toString()) } // stary sposob. Na novsich android to uz nefunguje kvoli "bezpecnosti"
  return FileProvider.getUriForFile(this, "com.parohy.storagemechanicum.fileprovider", document).also { Log.e("downloadPdfFile", it.toString()) }
}

/*
* Pristupujem k privátnemu externemu úložisku
* Tu moze citat subory len moja aplikacia
* */
fun Context.downloadPdfFile_private(name: String, inputStream: BufferedInputStream): Uri {
  val downloads = getExternalFilesDir(Environment.DIRECTORY_PICTURES)

  val document = File(downloads, name)

  if (!document.exists())
    document.createNewFile()

  val buffer = ByteArray(1024)
  document.outputStream().use { stream ->
    inputStream.read(buffer)
    do {
      stream.write(buffer)
    } while (inputStream.read(buffer) != -1)
  }

  Uri.fromFile(document).also { Log.e("downloadPdfFile", it.toString()) } // stary sposob. Na novsich android to uz nefunguje kvoli "bezpecnosti"
  return FileProvider.getUriForFile(this, "com.parohy.storagemechanicum.fileprovider", document).also { Log.e("downloadPdfFile", it.toString()) }
}