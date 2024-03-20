package com.parohy.storagemechanicum.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.parohy.storagemechanicum.databinding.UseCaseBinding

class ReadFromExternal : ComponentActivity() {
  private val binding by lazy { UseCaseBinding.inflate(layoutInflater) }

  private val pickImageRequest = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
    if (result.resultCode == Activity.RESULT_OK) {
      Log.e("ReadFromExternal", "result: ${result.data?.data}")
//      binding.imageView.setImageURI(result.data?.data)
    }
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    binding.apply {
      setContentView(root)

      title.text = "Read from External"

      button.setOnClickListener {
        pickImageRequest.launch(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
//          type = "image/*"
//          addCategory(Intent.CATEGORY_OPENABLE)
          flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
        })
      }
    }
  }
}
