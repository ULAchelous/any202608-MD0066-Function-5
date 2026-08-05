package io.ula.aiy.mb.utils

import android.content.Context
import android.net.Uri
import android.util.Base64
import java.io.InputStream

object FileUtil {
    fun uriToBase64(context: Context, uri: Uri): String? {
        return try {
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            val bytes = inputStream?.readBytes()
            inputStream?.close()
            if (bytes != null) {
                // Using Base64.NO_WRAP to avoid newline characters which might break JSON
                Base64.encodeToString(bytes, Base64.NO_WRAP)
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
