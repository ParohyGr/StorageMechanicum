package com.parohy.storagemechanicum.ui

import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
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
  private val requestPermissionContract = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
    callback?.invoke(granted)
    callback = null
  }

  private fun withPermission(action: (Boolean) -> Unit) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
      callback = action
      requestPermissionContract.launch(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
    } else action(true)
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
        withPermission { granted ->
          if (granted) send(Msg.DownloadToExternal)
          else Toast.makeText(this@StoreToExternal, "Permission denied", Toast.LENGTH_SHORT).show()}
      }
    }
  }
}
