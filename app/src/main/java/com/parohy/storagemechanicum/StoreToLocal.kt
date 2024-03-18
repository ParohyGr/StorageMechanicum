package com.parohy.storagemechanicum

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import com.parohy.storagemechanicum.databinding.UseCaseBinding

class StoreToLocal : ComponentActivity() {
  private val binding by lazy { UseCaseBinding.inflate(layoutInflater) }

  private val pickImageRequest = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
    if (result.resultCode == Activity.RESULT_OK)
      binding.imageView.setImageURI(result.data?.data)
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    binding.apply {
      setContentView(root)

      title.text = "Store to app storage"

      button.setOnClickListener {
        pickImageRequest.launch(Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI))
      }
    }
  }
}