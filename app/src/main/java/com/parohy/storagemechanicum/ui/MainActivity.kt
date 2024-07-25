package com.parohy.storagemechanicum.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import com.parohy.storagemechanicum.databinding.ActivityMainBinding

/*
* Android -22
* Android 23-28
* Android 29+
* */
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
      scopedStorage.setOnClickListener { startActivity(Intent(this@MainActivity, ScopedStorage::class.java)) }
    }
  }
}