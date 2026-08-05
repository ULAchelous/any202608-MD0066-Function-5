package io.ula.aiy.mb.ui.request.card

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.ula.aiy.mb.ui.theme.MythBustersTheme

data class ShareCardContent(
    val title: String,
    val intro: String,
    val factContent: String,
    val actionContent: String,
    val checkContent: String,
    val closing: String
)

@Composable
fun ShareCard(
    modifier: Modifier = Modifier,
    content: ShareCardContent
) {
    // 颜色定义
    val cardBg = Color(0xFFFDF8F1)
    val cardBorder = Color(0xFFE8DCC2)
    val titleColor = Color(0xFFA63B1D)
    val tagBg = Color(0xFFD86A3C)
    val textColor = Color(0xFF333333)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(cardBg, RoundedCornerShape(32.dp))
            .border(1.dp, cardBorder, RoundedCornerShape(32.dp))
            .padding(2.dp) // 外圈细线效果
            .border(1.dp, cardBorder.copy(alpha = 0.5f), RoundedCornerShape(30.dp))
            .padding(24.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.Start
        ) {
            // 标题
            Text(
                text = content.title,
                color = titleColor,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                textAlign = TextAlign.Center
            )

            // 开场白
            Text(
                text = content.intro,
                color = textColor,
                fontSize = 18.sp,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            // 板块：事实
            CardSection(tag = "事实", body = content.factContent, tagBg = tagBg)

            // 板块：怎么做
            CardSection(tag = "怎么做", body = content.actionContent, tagBg = tagBg)

            // 板块：自己动手查
            CardSection(tag = "自己动手查", body = content.checkContent, tagBg = tagBg)

            // 结语
            Text(
                text = content.closing,
                color = textColor,
                fontSize = 17.sp,
                lineHeight = 26.sp,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@Composable
private fun CardSection(
    tag: String,
    body: String,
    tagBg: Color
) {
    Column(modifier = Modifier.padding(bottom = 24.dp)) {
        Surface(
            color = tagBg,
            shape = RoundedCornerShape(8.dp)
        ) {
            Text(
                text = tag,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = body,
            color = Color(0xFF333333),
            fontSize = 18.sp,
            lineHeight = 28.sp
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFEEEEEE)
@Composable
fun ShareCardPreview() {
    val mockData = ShareCardContent(
        title = "低钠盐，不必一概拒绝",
        intro = "姑姑，您这条消息我看到啦。",
        factContent = "关心家里吃盐，您想得很周到。",
        actionContent = "这句话说得太绝对。",
        checkContent = "姑姑可看盐袋背面提示。再看看自己是否肾脏排钾不顺。或高温出汗多、常干重活。或正在吃降压药。这些情况先问医生。",
        closing = "普通人不必急着拒绝。选哪种盐，都要少放。您这样认真核对，很稳妥。"
    )
    MythBustersTheme {
        Box(modifier = Modifier.padding(16.dp)) {
            ShareCard(content = mockData)
        }
    }
}
