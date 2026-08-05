package io.ula.aiy.mb.ui.request.verify

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.gson.JsonObject
import io.ula.aiy.mb.utils.NetUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class Source(
    val title: String,
    val evidence: String,
    val publisher: String,
    val url: String
)

data class ClaimAnalysisData(
    val status: String,
    val riskLevel: String,
    val analysis: String,
    val sources: List<Source>
)

@Composable
fun ClaimAnalysisCard(
    modifier: Modifier = Modifier,
    data : ClaimAnalysisData,
    isLoading : Boolean
) {


    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(8.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = SolidColor(MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
        )
    ) {
        Column(
            modifier = Modifier
                .padding(20.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header with horizontal line
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "核验结果",
                    color = MaterialTheme.colorScheme.secondary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(8.dp))
                HorizontalDivider(
                    modifier = Modifier.weight(1f),
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            // Status Tags
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AnalysisStatusTag(
                text = when(data.status){
                    "credible" -> "基本可信"
                    "uncertain" -> "证据不足"
                    "misleading" -> "谣言"
                    else -> "加载中..."
                },
                icon = when(data.status){
                    "credible" -> Icons.Default.Check
                    "uncertain" -> Icons.Default.Info
                    "misleading" -> Icons.Default.Warning
                    else -> Icons.Default.Info
                },
                status = when(data.status){
                    "credible" -> Status.SAFE
                    "uncertain" -> Status.MEDIUM
                    "misleading" -> Status.RISK
                    else -> Status.MEDIUM
                }
            )
                AnalysisStatusTag(
                    text =" 风险：${when(data.riskLevel){
                        "low" -> "低"
                        "medium" -> "中"
                        "high" -> "高"
                        else -> "加载中..."
                    }}",
                    icon = when(data.riskLevel){
                        "low" -> Icons.Default.Check
                        "medium" -> Icons.Default.Info
                        "high" -> Icons.Default.Warning
                        else -> Icons.Default.Info
                    },
                    status = when(data.riskLevel){
                        "low" -> Status.SAFE
                        "medium" -> Status.MEDIUM
                        "high" -> Status.RISK
                        else -> Status.MEDIUM
                    }
                )

            }

            var isExpanded by remember{mutableStateOf(false)}
            // Analysis Text
            Text(
                text = data.analysis,
                style = MaterialTheme.typography.bodyLarge,
                lineHeight = 24.sp,
                color = MaterialTheme.colorScheme.onSurface
            )

            // Source Section Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
//                Icon(
//                    imageVector = Icons.AutoMirrored.Filled.List,
//                    contentDescription = null,
//                    tint = MaterialTheme.colorScheme.secondary,
//                    modifier = Modifier.size(20.dp)
//                )
                IconButton(
                    onClick = { isExpanded = !isExpanded },
                    modifier = Modifier.size(20.dp)
                ) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowRight,
                        contentDescription = if (isExpanded) "收起引用信源" else "展开引用信源",
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(30.dp)
                    )
                }
                Text(
                    text = "引用信源",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }



            // Source List
            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    data.sources.forEachIndexed { index, source ->
                        SourceItem(source)
                        if (index < data.sources.size - 1) {
                            HorizontalDivider(
                                modifier = Modifier.padding(top = 8.dp),
                                thickness = 0.5.dp,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }

            }
            // Disclaimer Box
            DisclaimerBox()
        }
    }
}

enum class Status{
    SAFE,
    MEDIUM,
    RISK
}

@Composable
fun AnalysisStatusTag(
    text: String,
    icon: ImageVector? = null,
    status: Status
) {

    var containerColor = when(status){
        Status.SAFE -> MaterialTheme.colorScheme.surfaceContainer
        Status.MEDIUM -> MaterialTheme.colorScheme.secondaryContainer
        Status.RISK -> MaterialTheme.colorScheme.errorContainer
    }
    var contentColor = when(status){
        Status.SAFE -> MaterialTheme.colorScheme.onSurfaceVariant
        Status.MEDIUM -> MaterialTheme.colorScheme.onSecondaryContainer
        Status.RISK -> MaterialTheme.colorScheme.onErrorContainer
    }
    Surface(
        color = containerColor,
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = contentColor
                )
            }
            Text(
                text = text,
                color = contentColor,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun SourceItem(source: Source) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = source.title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = source.evidence,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = source.publisher,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.clickable {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(source.url))
                context.startActivity(intent)
            }
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = source.url,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                textDecoration = TextDecoration.Underline,
                maxLines = 1
            )
        }
    }
}

@Composable
fun DisclaimerBox() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = "内容仅供健康信息核验，不能替代医生诊断。涉及医疗、用药决策时，请咨询专业人士。",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.secondary,
                lineHeight = 20.sp
            )
        }
    }
}

//@Preview(showBackground = true)
//@Composable
//fun ClaimAnalysisCardPreview() {
//    val sampleData = ClaimAnalysisData(
//        status = "暂时无法判断",
//        riskLevel = "中风险",
//        analysis = "现有资料支持丙烯颜料属于绘画材料、应避免误食，但不足以准确判断其与米饭共同蒸煮后的具体风险。其余资料主要讨论“丙烯”气体或丙烯腈，不能直接用于证明丙烯颜料的食用或加热安全性。因此，结论方向上应避免食用被丙烯颜料污染的米饭，但证据不足以对风险程度作确定判断。",
//        sources = listOf(
//            Source(
//                title = "丙烯颜料——彩绘颜料主力军",
//                publisher = "ChemicalBook",
//                url = "https://www.chemicalbook.com/NewsInfo_60700.htm"
//            ),
//            Source(
//                title = "丙烯 — 國家環境毒物研究中心",
//                publisher = "國家環境毒物研究中心",
//                url = "https://nehrc.nhri.edu.tw/2014/08/12/%E4%B8%8B%E8%BD%BD"
//            )
//        )
//    )
//    MythBustersTheme {
//        Box(modifier = Modifier.background(MaterialTheme.colorScheme.background).padding(16.dp)) {
//            ClaimAnalysisCard(data = sampleData)
//        }
//    }
//}
