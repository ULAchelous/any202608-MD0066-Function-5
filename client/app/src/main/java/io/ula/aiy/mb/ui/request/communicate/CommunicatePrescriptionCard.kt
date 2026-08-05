package io.ula.aiy.mb.ui.request.communicate

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.ula.aiy.mb.ui.theme.MythBustersTheme

data class CommunicatePrescription(
    val mainSuggestion: String,
    val rationale: String,
    val empathyContent: String,
    val factsContent: String,
    val adviceContent: String
)

@Composable
fun CommunicatePrescriptionCard(
    modifier: Modifier = Modifier,
    data: CommunicatePrescription,
    onDataChange: (CommunicatePrescription) -> Unit = {}
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = SolidColor(MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
        )
    ) {
        Column(
            modifier = Modifier
                .padding(20.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "沟通处方",
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

            // Suggestion Badge
            Surface(
                color = Color(0xFF1A1A1A), // 黑色背景
                shape = RoundedCornerShape(20.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = data.mainSuggestion,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Rationale text
            Text(
                text = data.rationale,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 22.sp
            )

            // Empathy Section (Pink)
            PrescriptionSection(
                title = "先共情",
                content = data.empathyContent,
                icon = Icons.Outlined.FavoriteBorder,
                containerColor = Color(0xFFFFF1F0),
                contentColor = Color(0xFFD86A3C),
                onContentChange = { onDataChange(data.copy(empathyContent = it)) }
            )

            // Facts Section (Blue)
            PrescriptionSection(
                title = "再说事实",
                content = data.factsContent,
                icon = Icons.Default.Search,
                containerColor = Color(0xFFF0F7FF),
                contentColor = Color(0xFF4A90E2),
                onContentChange = { onDataChange(data.copy(factsContent = it)) }
            )

            // Advice Section (Green)
            PrescriptionSection(
                title = "最后给建议",
                content = data.adviceContent,
                icon = Icons.Default.CheckCircle,
                containerColor = Color(0xFFF6FFED),
                contentColor = Color(0xFF52C41A),
                onContentChange = { onDataChange(data.copy(adviceContent = it)) }
            )
        }
    }
}

@Composable
private fun PrescriptionSection(
    title: String,
    content: String,
    icon: ImageVector,
    containerColor: Color,
    contentColor: Color,
    onContentChange: (String) -> Unit
) {
    Surface(
        color = containerColor,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = title,
                    color = contentColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }
            OutlinedTextField(
                value = content,
                onValueChange = onContentChange,
                modifier = Modifier.fillMaxWidth(),
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = 24.sp
                ),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = contentColor,
                    unfocusedBorderColor = contentColor.copy(alpha = 0.2f),
                    focusedContainerColor = Color.White.copy(alpha = 0.3f),
                    unfocusedContainerColor = Color.Transparent,
                    cursorColor = contentColor
                )
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun CommunicatePrescriptionCardPreview() {
    val mockData = CommunicatePrescription(
        mainSuggestion = "建议私下单独沟通",
        rationale = "最近有过争执，私下沟通更能避免让姑姑觉得被公开反驳，也方便根据她的身体情况温和说明。",
        empathyContent = "姑姑，我知道你提醒大家注意吃盐，是希望家里人都吃得健康，这份关心特别难得。",
        factsContent = "低钠盐有点像把一部分“钠”换成“钾”：在放盐量不增加的情况下，能少摄入一些钠，所以并不是所有人都需要避开。四川省卫生健康委员会、许昌市卫生健康委员会和疾控部门的科普都提到，它对一些人群有帮助，但肾功能不好、高钾血症、重体力或高温工作的人要慎用。",
        adviceContent = "咱们可以先看家里人的身体情况和用药情况，再决定用普通盐还是低钠盐；平时不管选哪种，控制总用盐量更重要。如果有人有肾病、血钾异常或正在吃降压药，先问问医生最稳妥。姑姑你帮大家提醒这些注意事项就很有用了。"
    )
    MythBustersTheme {
        Box(modifier = Modifier.background(MaterialTheme.colorScheme.background).padding(16.dp)) {
            CommunicatePrescriptionCard(data = mockData)
        }
    }
}
