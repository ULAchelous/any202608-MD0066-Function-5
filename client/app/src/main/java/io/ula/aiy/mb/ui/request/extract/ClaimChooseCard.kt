package io.ula.aiy.mb.ui.request.extract

import android.R
import android.content.ClipData
import android.text.Layout
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.ula.aiy.mb.ui.theme.MythBustersTheme

@Composable
fun ClaimChooseCard(claims: JsonArray, selectedId: MutableState<Int> = remember { mutableStateOf(0) }) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = CardDefaults.outlinedCardBorder()
            .copy(brush = SolidColor(MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)))
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
                    text = "选择并核验主张",
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

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(400.dp),
                contentPadding = PaddingValues(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                claims.forEachIndexed { index, element ->
                    var jsonObject = element.asJsonObject;
                    item {
                        ClaimSubCard(jsonObject, index, selectedId);
                    }
                }
            }
        }
    }
}



@Preview(showBackground = true)
@Composable
fun ClaimChooseCardPreview() {
    val mockClaims = JsonArray().apply {
        add(JsonObject().apply {
            addProperty("claim", "主张 1：这是一个测试主张")
            addProperty("evidence", "证据 1")
            addProperty("risk_hint", "low")
        })
        add(JsonObject().apply {
            addProperty("claim", "主张 2：这是另一个测试主张")
            addProperty("evidence", "证据 2")
            addProperty("risk_hint", "medium")
        })
        add(JsonObject().apply {
            addProperty("claim", "主张 3：这是第三个测试主张")
            addProperty("evidence", "证据 3")
            addProperty("risk_hint", "high")
        })
    }
    MythBustersTheme {
        ClaimChooseCard(claims = mockClaims)
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF5F5F5)
@Composable
private fun ClaimSubCardPreview() {
    val mockClaim = JsonObject().apply {
        addProperty("claim", "测试主张内容")
        addProperty("evidence", "测试证据内容")
        addProperty("risk_hint", "medium")
    }
    val selected = remember { mutableStateOf(0) }
    MythBustersTheme {
        ClaimSubCard(claim = mockClaim, id = 0, selected = selected)
    }
}

@Composable
private fun ClaimSubCard(claim: JsonObject,id : Int,selected : MutableState<Int>) {

    val isChecked = selected.value == id
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.secondary
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = CardDefaults.outlinedCardBorder()
            .copy(brush = SolidColor(MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)))
    ) {
         Row(
             modifier = Modifier.fillMaxWidth().padding(16.dp),
             horizontalArrangement = Arrangement.Start,
             verticalAlignment = Alignment.Top
         ){
             // 自定义圆角 Checkbox
             Surface(
                 modifier = Modifier.size(24.dp),
                 shape = RoundedCornerShape(8.dp),
                 color = if (isChecked) MaterialTheme.colorScheme.primary else Color.Transparent,
                 border = BorderStroke(
                     width = 2.dp,
                     color = if (isChecked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                 ),
                 onClick = { selected.value = id }
             ) {
                 Box(contentAlignment = Alignment.Center) {
                     if (isChecked) {
                         Icon(
                             imageVector = Icons.Default.Check,
                             contentDescription = null,
                             tint = MaterialTheme.colorScheme.onPrimary,
                             modifier = Modifier.size(16.dp)
                         )
                     }
                 }
             }

             Column(modifier = Modifier.fillMaxSize()) {
                 Text(
                     text = claim.get("claim")?.asString ?: "",
                     modifier = Modifier.padding(start = 12.dp),
                     style = MaterialTheme.typography.titleMedium
                 )
                 Text(
                     text = claim.get("evidence")?.asString ?: "",
                     modifier = Modifier.padding(start = 12.dp),
                     style = MaterialTheme.typography.bodyMedium
                 )
                 Text(
                     text = "风险：${when(claim.get("risk_hint")?.asString){
                         "low" -> "低"
                         "medium" -> "中"
                         "high" -> "高"
                         else -> "加载中"
                     }}",
                     modifier = Modifier.padding(start = 12.dp),
                     style = MaterialTheme.typography.titleMedium
                 )
             }
         }
    }
}
