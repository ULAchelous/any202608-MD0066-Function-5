package io.ula.aiy.mb.ui.request.extract

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.ula.aiy.mb.R
import io.ula.aiy.mb.ui.common.LoadingProgressCard
import io.ula.aiy.mb.ui.request.SRC_TYPE
import io.ula.aiy.mb.ui.theme.MythBustersTheme
import io.ula.aiy.mb.utils.NetUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.tooling.preview.Preview

@Composable
fun ExtractScreen(
    modifier: Modifier = Modifier,
    type: SRC_TYPE,
    content: String,
    onNavigateToVerify: (String, JsonObject) -> Unit
) {
    val apiUrl = stringResource(R.string.net_backend_url)




    // 1. 先解析核心主张

    var postData by remember { mutableStateOf(JsonObject()) }
    var isLoading by remember { mutableStateOf(true) }
    val selectedId = remember { mutableStateOf(0) }

    LaunchedEffect(content) {
        if (content.isNotBlank()) {
            isLoading = true

            withContext(Dispatchers.IO) {
                val body = JsonObject().apply {
                    val typeStr = when (type) {
                        SRC_TYPE.WX_ARTICLE -> "wechat_url"
                        SRC_TYPE.TEXT -> "text"
                        SRC_TYPE.IMAGE -> "image"
                    }
                    addProperty("type", typeStr)
                    addProperty("content", content)
                }
                val url = apiUrl + "/api/extract"
                val netUtil = NetUtil()
                val result = netUtil.postData(url, body)
                postData = result
                isLoading = false
            }
        } else {
            isLoading = false
        }
    }
    // 2. 等 extractedClaim 拿到后再核验


    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Spacer(Modifier.size(30.dp))
        }
        item {
            Text(
                text = if (isLoading) "正在分析..." else "分析完成",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        item {
            MythAnalysisCard(postData,isLoading,type)
        }

        item {
            if (isLoading) {
                LoadingProgressCard(
                    title = "选择并核验主张",
                    loadingText = "正在提取关键主张"
                )
            } else {
                ClaimChooseCard(
                    claims = if (postData.has("claims")) postData.getAsJsonArray("claims") else JsonArray(),
                    selectedId = selectedId
                )
            }
        }

        if (!isLoading && postData.has("claims")) {
            item {
                Button(
                    onClick = {
                        val claimsArray = postData.getAsJsonArray("claims")
                        val selectedClaim = if (selectedId.value < claimsArray.size()) {
                            claimsArray[selectedId.value].asJsonObject.get("claim")?.asString ?: ""
                        } else ""
                        onNavigateToVerify(selectedClaim, postData)
                    },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                ) {
                    Text("开始核验")
                }
            }
        }

    }
}

@Preview(showBackground = true)
@Composable
fun ExtractScreenLoadingPreview() {
    MythBustersTheme {
        LoadingProgressCard(
            title = "选择并核验主张",
            loadingText = "正在提取关键主张"
        )
    }
}

@Preview(showBackground = true)
@Composable
fun ExtractScreenLoadedPreview() {
    val mockPostData = JsonObject().apply {
        addProperty("claim", "测试核心主张")
        addProperty("topic_summary", "这是一个测试摘要内容")
        add("patterns", JsonArray().apply {
            add("夸大因果")
            add("断章取义")
        })
        add("claims", JsonArray().apply {
            add(JsonObject().apply {
                addProperty("claim", "测试主张 1")
                addProperty("evidence", "测试证据 1")
                addProperty("risk_hint", "low")
            })
            add(JsonObject().apply {
                addProperty("claim", "测试主张 2")
                addProperty("evidence", "测试证据 2")
                addProperty("risk_hint", "high")
            })
        })
    }
    MythBustersTheme {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    text = "分析完成",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            item {
                MythAnalysisCard(mockPostData, false, SRC_TYPE.TEXT)
            }
            item {
                ClaimChooseCard(
                    claims = mockPostData.getAsJsonArray("claims")
                )
            }
        }
    }
}