package com.parohy.storagemechanicum.ui

import android.Manifest
import android.content.pm.PackageManager
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

class ScopedStorage : ComponentActivity() {
  private val binding by lazy { UseCaseBinding.inflate(layoutInflater) }

  private var actionOnPermission: ((Boolean) -> Unit)? = null
  private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
    actionOnPermission?.invoke(granted)
  }

  private fun doIfWritePermissions(action: (Boolean) -> Unit) {
    val hasWritePermission = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
    if (hasWritePermission || Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      action(true)
      return
    }

    actionOnPermission = action
    permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
  }

  private fun doIfReadPermissions(action: (Boolean) -> Unit) {
    val hasReadPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
    if (hasReadPermission) {
      action(true)
      return
    }

    actionOnPermission = action
    permissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    binding.apply {
      setContentView(root)

      title.text = "Scoped storage"

      root.bind(state.map { it.downloadExternal }.flow) { state ->
        error.text = null

        when (state) {
          is Content -> imageView.setImageURI(state.value)
          is Failure -> error.text = state.value.toString()
          is Loading -> Toast.makeText(this@ScopedStorage,  "LOADING...", Toast.LENGTH_SHORT).show()
          null       -> {}
        }

        state.ifFailure { error.text = it.toString() }
      }

      button.setOnClickListener {
        doIfWritePermissions {
          send(Msg.DownloadImageToScopedStorage)
        }
      }
    }
  }
}
