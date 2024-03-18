package com.parohy.storagemechanicum

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.parohy.storagemechanicum.databinding.ActivityMainBinding
import com.parohy.storagemechanicum.databinding.UseCaseBinding

class MainActivity : ComponentActivity() {
  private val binding by lazy { ActivityMainBinding.inflate(layoutInflater) }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    binding.apply {
      setContentView(root)

      storeLocal.setOnClickListener { startActivity(Intent(this@MainActivity, StoreToLocal::class.java)) }
      readLocal.setOnClickListener { startActivity(Intent(this@MainActivity, ReadFromLocal::class.java)) }
      storeExternal.setOnClickListener { startActivity(Intent(this@MainActivity, StoreToExternal::class.java)) }
      readExternal.setOnClickListener { startActivity(Intent(this@MainActivity, ReadFromExternal::class.java)) }
    }
  }
}