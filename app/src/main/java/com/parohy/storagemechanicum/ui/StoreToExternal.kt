package com.parohy.storagemechanicum.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.parohy.storagemechanicum.Msg
import com.parohy.storagemechanicum.databinding.UseCaseBinding
import com.parohy.storagemechanicum.send
import com.parohy.storagemechanicum.state
import common.bind
import shared.Content
import shared.Failure
import shared.Loading
import shared.flow
import shared.ifFailure
import shared.map

class StoreToExternal : ComponentActivity() {
  private val binding by lazy { UseCaseBinding.inflate(layoutInflater) }

  private var callback: ((Boolean) -> Unit)? = null
  private val requestPermissionContract = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
    callback?.invoke(granted.all { it.value })
    callback = null
  }

  private fun downloadWithPermission(action: (Boolean) -> Unit) {
    callback = action
    if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
      requestPermissionContract.launch(arrayOf(android.Manifest.permission.WRITE_EXTERNAL_STORAGE))
    } else if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_MEDIA_IMAGES) == android.content.pm.PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(this, android.Manifest.permission.WRITE_EXTERNAL_STORAGE) == android.content.pm.PackageManager.PERMISSION_GRANTED)
        action(true)
    else {
      val permissions = arrayOf(android.Manifest.permission.READ_MEDIA_IMAGES)
      if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q)
        permissions.plus(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)

      requestPermissionContract.launch(permissions)
    }
  }

  private var fileCallback: ((Uri?) -> Unit)? = null
  private val createFileRequest = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
    if (result.resultCode == Activity.RESULT_OK) {
      fileCallback?.invoke(result.data?.data)
      fileCallback = null
    }
  }

  private fun downloadWithSAF(action: (Uri?) -> Unit) {
    fileCallback = action
    createFileRequest.launch(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
      type = "image/*"
      addCategory(Intent.CATEGORY_OPENABLE)
      putExtra(Intent.EXTRA_TITLE, "image_external_SAF.jpg")
      flags = Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
    })
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    binding.apply {
      setContentView(root)

      title.text = "Store to External"

      root.bind(state.map { it.downloadExternal }.flow) { state ->
        error.text = null

        when (state) {
          is Content -> imageView.setImageURI(state.value)
          is Failure -> error.text = state.value.toString()
          is Loading -> Toast.makeText(this@StoreToExternal,  "LOADING...", Toast.LENGTH_SHORT).show()
          null       -> {}
        }

        state.ifFailure { error.text = it.toString() }
      }

      button.setOnClickListener {
        downloadWithPermission { granted ->
          if (granted) send(Msg.DownloadToExternal)
          else Toast.makeText(this@StoreToExternal, "Permission denied", Toast.LENGTH_SHORT).show()
        }

//        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
//          downloadWithPermission { granted ->
//            if (granted) send(Msg.DownloadToExternal)
//            else Toast.makeText(this@StoreToExternal, "Permission denied", Toast.LENGTH_SHORT).show()}
//        } else downloadWithSAF {  uri ->
//          if (uri != null) send(Msg.DownloadToExternalSAF(uri))
//          else Toast.makeText(this@StoreToExternal, "SAF failed", Toast.LENGTH_SHORT).show()
//        }
      }
    }
  }
}
