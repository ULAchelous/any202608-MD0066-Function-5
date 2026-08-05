package io.ula.aiy.mb.ui.request.card

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.view.View
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.gson.JsonObject
import io.ula.aiy.mb.R
import io.ula.aiy.mb.ui.common.LoadingProgressCard
import io.ula.aiy.mb.ui.theme.MythBustersTheme
import io.ula.aiy.mb.utils.NetUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStream

@Composable
fun CardScreen(
    modifier: Modifier = Modifier,
    extractData: JsonObject,
    verifyData: JsonObject,
    target: String = "elder",
    onBackToMain: () -> Unit
) {
    val context = LocalContext.current
    val apiUrl = stringResource(R.string.net_backend_url)

    var selectedStyle by remember { mutableStateOf("elder") } // "elder" or "group_notice"
    var cardContent by remember { mutableStateOf<ShareCardContent?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // 用来引用内部的 AndroidView 以便截屏
    var captureView by remember { mutableStateOf<View?>(null) }

    LaunchedEffect(selectedStyle, verifyData) {
        if (verifyData.has("claim")) {
            isLoading = true
            errorMessage = null
            cardContent = null

            val result = withContext(Dispatchers.IO) {
                val body = JsonObject().apply {
                    addProperty("claim", verifyData.get("claim").asString)
                    if (verifyData.has("verdict")) addProperty("verdict", verifyData.get("verdict").asString)
                    if (verifyData.has("risk_level")) addProperty("risk_level", verifyData.get("risk_level").asString)
                    if (verifyData.has("summary")) addProperty("summary", verifyData.get("summary").asString)
                    addProperty("target", target)
                    addProperty("style", selectedStyle)
                    if (verifyData.has("sources")) add("sources", verifyData.get("sources").asJsonArray)
                }

                val url = "$apiUrl/api/card"
                val netUtil = NetUtil()
                netUtil.postData(url, body)
            }

            if (result.has("title")) {
                cardContent = ShareCardContent(
                    title = result.get("title").asString,
                    intro = result.get("greeting")?.asString ?: "",
                    factContent = result.get("fact")?.asString ?: "",
                    actionContent = result.get("suggestion")?.asString ?: "",
                    checkContent = result.get("self_verify")?.asString ?: "",
                    closing = result.get("closing")?.asString ?: ""
                )
            } else {
                errorMessage = "卡片内容获取失败，请重试"
            }
            isLoading = false
        }
    }

    Scaffold(
        bottomBar = {
            if (!isLoading && cardContent != null) {
//                Surface(
//                    modifier = Modifier.fillMaxWidth(),
//                    tonalElevation = 8.dp,
//                    shadowElevation = 8.dp
//                ) {
                    Row(
                        modifier = Modifier
                            .padding(16.dp)
                            .navigationBarsPadding(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = onBackToMain,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        ) {
                            Text("返回首页")
                        }
                        Button(
                            onClick = {
                                captureView?.let { view ->
                                    val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                                    val canvas = Canvas(bitmap)
                                    view.draw(canvas)
                                    saveImageToGallery(context, bitmap, "安心核验卡.png")
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("保存到相册")
                        }
                    }
 //               }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "预览并分享",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            item {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = selectedStyle == "elder",
                        onClick = { selectedStyle = "elder" },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                    ) {
                        Text("长辈版")
                    }
                    SegmentedButton(
                        selected = selectedStyle == "group_notice",
                        onClick = { selectedStyle = "group_notice" },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                    ) {
                        Text("群公告版")
                    }
                }
            }

            item {
                when {
                    isLoading -> {
                        LoadingProgressCard(
                            title = "生成中",
                            loadingText = "正在构思沟通文案",
                            isLoading = true
                        )
                    }
                    cardContent != null -> {
                        // 使用 AndroidView 包装 ShareCard 以便截屏保存
                        AndroidView(
                            factory = { ctx ->
                                ComposeView(ctx).apply {
                                    setContent {
                                        MythBustersTheme {
                                            ShareCard(content = cardContent!!)
                                        }
                                    }
                                    captureView = this
                                }
                            },
                            update = { view ->
                                view.setContent {
                                    MythBustersTheme {
                                        ShareCard(content = cardContent!!)
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    errorMessage != null -> {
                        Text(
                            text = errorMessage!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    else -> {
                        Text("暂无数据", color = MaterialTheme.colorScheme.outline)
                    }
                }
            }
            
            item {
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

fun saveImageToGallery(context: Context, bitmap: Bitmap, name: String) {
    val filename = "${name.removeSuffix(".png")}_${System.currentTimeMillis()}.png"
    val contentValues = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
        put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/MythBusters")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
    }

    val resolver = context.contentResolver
    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

    try {
        uri?.let {
            val outputStream: OutputStream? = resolver.openOutputStream(it)
            outputStream?.use { os ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, os)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(it, contentValues, null, null)
            }
            Toast.makeText(context, "已保存到相册", Toast.LENGTH_SHORT).show()
        } ?: Toast.makeText(context, "保存失败：无法创建文件", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        Toast.makeText(context, "保存失败: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

@Preview(showBackground = true)
@Composable
fun CardScreenPreview() {
    MythBustersTheme {
        CardScreen(
            extractData = JsonObject(),
            verifyData = JsonObject().apply {
                addProperty("claim", "测试主张")
            },
            onBackToMain = {}
        )
    }
}
