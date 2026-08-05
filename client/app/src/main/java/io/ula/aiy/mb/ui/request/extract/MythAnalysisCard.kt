package io.ula.aiy.mb.ui.request.extract

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.gson.JsonObject
import io.ula.aiy.mb.ui.request.SRC_TYPE
import io.ula.aiy.mb.ui.theme.ClaudeAccentOrange
import io.ula.aiy.mb.utils.NetUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun MythAnalysisCard(postData : JsonObject, isLoading: Boolean, type: SRC_TYPE) {

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)))
    ) {
        Column(
            modifier = Modifier
                .padding(20.dp)
                .fillMaxWidth()
        ) {
            // 顶部 Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "核心主张",
                    color = MaterialTheme.colorScheme.secondary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(8.dp))
                HorizontalDivider(
                    modifier = Modifier.weight(1f),
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            // 主体内容（带橙色引号）
            if (isLoading) {
                MainContent("加载中...")
            } else if (postData.has("topic_summary")) {
                MainContent(postData.get("topic_summary").asString)
            } else {
                MainContent("无法解析内容或返回数据格式不正确")
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 底部标签 FlowRow (自动换行)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if(isLoading){
                    AnalysisTag(text = "加载中")
                }else{
                    if(postData != null && postData != JsonObject()){
                        if(postData.has("patterns")){
                            val patterns = postData.get("patterns").asJsonArray
                            // 限制最多显示 8 个
                            patterns.take(8).forEach {
                                AnalysisTag(it.asString)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MainContent(text: String) {
    Box {
        Text(
            text = buildAnnotatedString {
                withStyle(style = SpanStyle(color = ClaudeAccentOrange, fontSize = 24.sp)) {
                    append("“")
                }
                withStyle(style = SpanStyle(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.SemiBold
                )) {
                    append(text)
                }
                withStyle(style = SpanStyle(color = ClaudeAccentOrange, fontSize = 24.sp)) {
                    append(" ”")
                }
            },
            lineHeight = 30.sp
        )
    }
}
@Composable
fun AnalysisTag(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = text,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}
