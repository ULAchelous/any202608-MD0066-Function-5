package io.ula.aiy.mb.ui.request.communicate

import android.view.inputmethod.InlineSuggestion
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.google.gson.JsonObject
import io.ula.aiy.mb.R
import io.ula.aiy.mb.ui.common.LoadingProgressCard
import io.ula.aiy.mb.ui.request.SRC_TYPE
import io.ula.aiy.mb.ui.theme.MythBustersTheme
import io.ula.aiy.mb.utils.NetUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withContext


@Composable
fun CommunicateScreen(
    modifier: Modifier = Modifier,
    claim: String,
    onBackToMain: () -> Unit,
    onNavigateToCard: (String) -> Unit
) {
    var relationshipContent by remember { mutableStateOf(RelationshipContent()) }
    var startLoading by remember { mutableStateOf(false) }
    var stopLoading by remember { mutableStateOf(false) }
    val apiUrl = stringResource(R.string.net_backend_url)
    var postData by remember { mutableStateOf(JsonObject()) }
    var prescription by remember { mutableStateOf<CommunicatePrescription?>(null) }

    LaunchedEffect(postData) {
        if (postData.has("communication")) {
            val communication = postData.getAsJsonObject("communication")
            prescription = CommunicatePrescription(
                mainSuggestion = when(communication.get("channel").asString){
                    "private_chat" -> "私下沟通"
                    else -> "自主沟通"
                },
                rationale = communication.get("reason").asString,
                empathyContent = communication.get("opening").asString,
                factsContent = communication.get("fact").asString,
                adviceContent = communication.get("suggestion").asString
            )
        }
    }

    LaunchedEffect(startLoading) {
        if(startLoading) {
            val result = withContext(Dispatchers.IO) {
                val body = JsonObject().apply {
                    addProperty("claim", claim)
                    addProperty("relationship_state", relationshipContent.state)
                    addProperty("target", relationshipContent.target)
                }
                val url = apiUrl + "/api/verify"
                val netUtil = NetUtil()
                netUtil.postData(url, body)
            }
            postData = result
            stopLoading = true
        }
    }

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
                text = "提交亲属关系信息",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
        item {
            if(!startLoading) {
                InputRelationshipCard(
                    onContentChange = { relationshipContent = it }
                )
            }
        }

        item{
/**/       if(!startLoading){
                Button(
                    onClick = {
                        if(checkContent(relationshipContent))
                            startLoading = true
                    },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                ) {
                    Text("生成沟通方案")
                }
            }else{
                if (!stopLoading) {
                    LoadingProgressCard(title = "生成方案", loadingText = "沟通方案生成中...", isLoading = true)
                } else {
                    prescription?.let { data ->
                        CommunicatePrescriptionCard(
                            data = data,
                            onDataChange = { prescription = it }
                        )
                        Button(
                            onClick = { onNavigateToCard(relationshipContent.target) },
                            modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
                        ) {
                            Text("生成并分享卡片")
                        }
                    }
                }
            }
        }
    }
}

fun checkContent(content: RelationshipContent): Boolean{
    return  content.target.isNotBlank() &&
            content.state.isNotBlank()
}

@Preview(showBackground = true)
@Composable
fun CommunicateScreenPreview() {
    MythBustersTheme {
        CommunicateScreen(claim = "测试主张", onBackToMain = {}, onNavigateToCard = {})
    }
}
