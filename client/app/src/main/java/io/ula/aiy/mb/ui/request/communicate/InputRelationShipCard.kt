package io.ula.aiy.mb.ui.request.communicate

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import io.ula.aiy.mb.ui.common.CustomDropdown
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.ula.aiy.mb.ui.theme.MythBustersTheme


class RelationshipContent(
    val target: String = "",
    val state: String = "",
    val additionalInfo: String = ""
)
@Composable
fun InputRelationshipCard(
    onContentChange: (RelationshipContent) -> Unit
) {
    val relationshipOptions = listOf("妈妈", "爸爸", "爷爷", "奶奶", "外公", "外婆", "姑姑/阿姨","叔叔/舅舅")
    var relExpanded by remember { mutableStateOf(false) }
    var selectedRel by remember { mutableStateOf(relationshipOptions[0]) }

    val statusOptions = listOf("最近有过争执", "很亲密，什么都能聊", "正常，偶尔联系", "有点紧张，说话要注意")
    var statusExpanded by remember { mutableStateOf(false) }
    var selectedStatus by remember { mutableStateOf(statusOptions[0]) }

    var additionalInfo by remember { mutableStateOf("") }

    // 状态提升：任一控件值变化时，实时上报给父级
    LaunchedEffect(selectedRel, selectedStatus, additionalInfo) {
        onContentChange(
            RelationshipContent(
                target = selectedRel,
                state = selectedStatus,
                additionalInfo = additionalInfo
            )
        )
    }

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
                    text = "输入关系信息",
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

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "方案 Agent 会根据你们的关系，生成更有人情味的话术——先共情，再说事。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 22.sp
            )

            Spacer(modifier = Modifier.height(24.dp))

            // 1. 沟通对象
            SectionTitle(icon = Icons.Outlined.Person, title = "沟通对象")
            CustomDropdown(
                options = relationshipOptions,
                selectedOption = selectedRel,
                expanded = relExpanded,
                onExpandedChange = { relExpanded = it },
                onOptionSelect = { selectedRel = it }
            )

            Spacer(modifier = Modifier.height(20.dp))

            // 2. 关系状态
            SectionTitle(icon = Icons.Outlined.FavoriteBorder, title = "你们最近的关系状态")
            CustomDropdown(
                options = statusOptions,
                selectedOption = selectedStatus,
                expanded = statusExpanded,
                onExpandedChange = { statusExpanded = it },
                onOptionSelect = { selectedStatus = it }
            )

            Spacer(modifier = Modifier.height(20.dp))

            // 3. 补充信息
            SectionTitle(icon = Icons.Default.Add, title = "补充信息 (可选)")
            OutlinedTextField(
                value = additionalInfo,
                onValueChange = { if (it.length <= 1800) additionalInfo = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(
                        text = "可以详细写：近期关系、既往争执、长辈的病史和用药情况、消息发在私聊还是家族群、你最担心的沟通后果等...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline
                    )
                },
                minLines = 4,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                    focusedBorderColor = MaterialTheme.colorScheme.primary
                )
            )
            
            Text(
                text = "${additionalInfo.length} / 1800",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier
                    .align(Alignment.End)
                    .padding(top = 4.dp)
            )
        }
    }
}

@Composable
private fun SectionTitle(icon: ImageVector, title: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(bottom = 12.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Preview(showBackground = true)
@Composable
fun InputRelationshipCardPreview() {
    MythBustersTheme {
        Box(modifier = Modifier.padding(16.dp)) {
            InputRelationshipCard(onContentChange = {})
        }
    }
}