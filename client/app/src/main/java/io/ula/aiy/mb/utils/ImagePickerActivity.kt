package io.ula.aiy.mb.utils

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import io.ula.aiy.mb.ui.request.RequestActivity
import io.ula.aiy.mb.ui.request.SRC_TYPE

class ImagePickerActivity : ComponentActivity() {

    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val base64 = FileUtil.uriToBase64(this, uri)
            if (base64 != null) {
                // Store large base64 data in-memory to avoid exceeding the
                // Binder transaction limit when launching RequestActivity.
                SharedDataHolder.putImageBase64(base64)
                val intent = Intent(this, RequestActivity::class.java).apply {
                    putExtra("type", SRC_TYPE.IMAGE.name)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(intent)
            }
        }
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pickImage.launch("image/*")
    }
}
