package io.ula.aiy.mb.ui.request.verify

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
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
import com.google.gson.JsonObject
import io.ula.aiy.mb.R
import io.ula.aiy.mb.ui.common.LoadingProgressCard
import io.ula.aiy.mb.ui.theme.MythBustersTheme
import io.ula.aiy.mb.utils.NetUtil
import kotlinx.coroutines.Dispatchers
import androidx.compose.ui.tooling.preview.Preview
import kotlinx.coroutines.withContext

@Composable
fun VerifyScreen(
    modifier: Modifier = Modifier,
    claim: String,
    onNavigateToCommunicate: (String, JsonObject) -> Unit
) {
    val apiUrl = stringResource(R.string.net_backend_url)
    var rawResult by remember { mutableStateOf(JsonObject()) }
    var data by remember {
        mutableStateOf(
            ClaimAnalysisData(
                status = "加载中",
                riskLevel = "加载中",
                analysis = "加载中",
                sources = emptyList()
            )
        )
    }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(claim) {
        if (claim.isNotBlank()) {
            isLoading = true

            withContext(Dispatchers.IO) {
                val body = JsonObject().apply {
                    addProperty("claim", claim)
                }
                val url = apiUrl + "/api/verify"
                val netUtil = NetUtil()
                val result = netUtil.postData(url, body)
                if (result != JsonObject()) {
                    var summary: String = "请求失败或无法解析"
                    var verdict: String = "暂时无法分析"
                    var risk_level: String = "暂时无法分析"
                    val srcs = mutableListOf<Source>()
                    if (result.has("summary"))
                        summary = result.get("summary").asString
                    if (result.has("verdict"))
                        verdict = result.get("verdict").asString
                    if (result.has("risk_level"))
                        risk_level = result.get("risk_level").asString
                    if (result.has("sources")) {
                        result.get("sources").asJsonArray.forEach { element ->
                            val jsonObj = element.asJsonObject
                            val src = Source(
                                title = jsonObj.get("title").asString,
                                url = jsonObj.get("url").asString,
                                publisher = jsonObj.get("publisher").asString,
                                evidence = jsonObj.get("evidence").asString,
                            )
                            srcs.add(src)
                        }
                    }
                    data = ClaimAnalysisData(
                        status = verdict,
                        riskLevel = risk_level,
                        analysis = summary,
                        sources = srcs
                    )
                    rawResult = result
                }
                isLoading = false
            }
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
    ) {
        item {
            Spacer(Modifier.size(30.dp))
        }
        item {
            Text(
                text = if (isLoading) "正在核验..." else "核验完成",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        if (isLoading) {
            item {
                LoadingProgressCard(
                    title = "核验结果",
                    loadingText = "正在核验主张"
                )
            }
        } else {
            item {
                ClaimAnalysisCard(data = data, isLoading = isLoading)
            }
            item {
                Button(
                    onClick = { onNavigateToCommunicate(claim, rawResult) },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                ) {
                    Text("前往生成方案")
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun VerifyScreenLoadingPreview() {
    MythBustersTheme {
        LoadingProgressCard(
            title = "核验结果",
            loadingText = "正在核验主张"
        )
    }
}

@Preview(showBackground = true)
@Composable
fun VerifyScreenLoadedPreview() {
    val mockData = ClaimAnalysisData(
        status = "uncertain",
        riskLevel = "medium",
        analysis = "现有资料支持部分主张，但不足以完全证实。建议进一步查证。",
        sources = listOf(
            Source(
                title = "来源一：测试文章标题",
                publisher = "测试发布者",
                url = "https://example.com/source1",
                evidence = "相关证据文本"
            ),
            Source(
                title = "来源二：另一个测试标题",
                publisher = "另一个发布者",
                url = "https://example.com/source2",
                evidence = "另一段证据"
            )
        )
    )
    MythBustersTheme {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
        ) {
            item {
                Text(
                    text = "核验完成",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            item {
                ClaimAnalysisCard(data = mockData, isLoading = false)
            }
        }
    }
}
