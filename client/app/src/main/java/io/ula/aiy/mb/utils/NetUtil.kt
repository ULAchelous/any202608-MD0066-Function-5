package io.ula.aiy.mb.utils


import android.util.Log
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import okhttp3.ConnectionSpec
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okio.IOException
import org.conscrypt.Conscrypt
import java.net.URL
import java.security.Security
import java.util.concurrent.TimeUnit

class NetUtil {

    companion object {
        init {
            try {
                Security.insertProviderAt(Conscrypt.newProvider(), 1)
            } catch (e: Exception) {
                Log.e("NetUtil", "Failed to install Conscrypt", e)
            }
        }
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(120, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .connectionSpecs(listOf(ConnectionSpec.MODERN_TLS, ConnectionSpec.COMPATIBLE_TLS, ConnectionSpec.CLEARTEXT))
        .build()
    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    val gson = GsonBuilder()
        .setPrettyPrinting()
        .create()

    public fun postData(url: String,body: JsonObject): JsonObject{
        val json = gson.toJson(body)
        Log.d("NetUtil", "Request URL: $url")
        Log.d("NetUtil", "Request Body: $json")
        
        val requestBody = json.toRequestBody(JSON_MEDIA_TYPE)
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "MythBusters/1.0 (Android)")
            .post(requestBody)
            .build()

        return try{
            client.newCall(request).execute().use{ response ->
                Log.d("NetUtil", "Response Code: ${response.code}")
                if(!response.isSuccessful){
                    Log.e("NetUtil", "Response Error: ${response.message}")
                    return JsonObject()
                }
                val responseString = response.body?.string() ?: return JsonObject()
                Log.d("NetUtil", "Response Body: $responseString")
                JsonParser.parseString(responseString).asJsonObject
            }
        }catch (e : Exception){
            Log.e("NetUtil", "Network Error", e)
            JsonObject()
        }
    }
}