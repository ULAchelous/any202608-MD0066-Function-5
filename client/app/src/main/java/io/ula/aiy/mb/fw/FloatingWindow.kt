package io.ula.aiy.mb.fw

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.ula.aiy.mb.ui.request.RequestActivity
import io.ula.aiy.mb.ui.request.SRC_TYPE
import io.ula.aiy.mb.ui.theme.MythBustersTheme
import kotlinx.coroutines.delay

@Composable
fun FloatingContent(
    onDrag: (Float, Float) -> Unit,
    onFocusChange: (Boolean) -> Unit = {},
    onExpandChange: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val textFieldFocusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    var isExpanded by remember { mutableStateOf(false) }
    var textInput by remember { mutableStateOf("") }
    var showTextDialog by remember { mutableStateOf(false) }
    var plainTextInput by remember { mutableStateOf("") }

    LaunchedEffect(isExpanded) {
        if (isExpanded) {
            delay(100)
            textFieldFocusRequester.requestFocus()
        } else {
            focusManager.clearFocus()
        }
    }

    MythBustersTheme() {
        Box(
            modifier = Modifier
                .wrapContentSize()
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        onDrag(dragAmount.x, dragAmount.y)
                    }
                }
                .padding(8.dp),
            contentAlignment = Alignment.BottomEnd
        ) {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.Bottom
            ) {
                AnimatedVisibility(
                    visible = isExpanded,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Column(
                        modifier = Modifier.width(200.dp),
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.Bottom
                    ) {
                        // Upload Image Button
                        FloatingActionButton(
                            modifier = Modifier.height(48.dp).width(160.dp),
                            onClick = {
                                val intent = Intent(context, io.ula.aiy.mb.utils.ImagePickerActivity::class.java).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(intent)
                                isExpanded = false
                                onExpandChange(false)
                            },
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            elevation = FloatingActionButtonDefaults.elevation(0.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxSize(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("上传图片", fontSize = 14.sp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Icon(
                                    Icons.Default.Create,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Submit Text Button
                        FloatingActionButton(
                            modifier = Modifier.height(48.dp).width(160.dp),
                            onClick = {
                                showTextDialog = true
                                isExpanded = false
                                onExpandChange(false)
                                onFocusChange(true)
                            },
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            elevation = FloatingActionButtonDefaults.elevation(0.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxSize(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("输入文本", fontSize = 14.sp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Icon(
                                    Icons.Default.Edit,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Link Input Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(
                                        MaterialTheme.colorScheme.surfaceVariant,
                                        RoundedCornerShape(12.dp)
                                    )
                            ) {
                                OutlinedTextField(
                                    value = textInput,
                                    onValueChange = { textInput = it },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(60.dp)
                                        .focusRequester(textFieldFocusRequester)
                                        .onFocusChanged { forceState ->
                                            onFocusChange(forceState.isFocused)
                                        },
                                    label = { Text("分析链接", fontSize = 12.sp) },
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        unfocusedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
                                        focusedBorderColor = MaterialTheme.colorScheme.primary
                                    )
                                )
                            }

                            FloatingActionButton(
                                modifier = Modifier.size(48.dp),
                                onClick = {
                                    if (textInput.isNotBlank()) {
                                        val intent =
                                            Intent(context, RequestActivity::class.java).apply {
                                                putExtra("link_data", textInput)
                                                putExtra("type", SRC_TYPE.WX_ARTICLE.name)
                                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            }
                                        context.startActivity(intent)
                                    }
                                },
                                shape = RoundedCornerShape(16.dp),
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                                elevation = FloatingActionButtonDefaults.elevation(0.dp)
                            ) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = "send",
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }

                FloatingActionButton(
                    onClick = {
                        isExpanded = !isExpanded
                        onExpandChange(isExpanded)
                    },
                    containerColor = if (isExpanded) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.primary,
                    contentColor = if (isExpanded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onPrimary,
                    elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp)
                ) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.Close else Icons.Default.Add,
                        contentDescription = if (isExpanded) "收起" else "展开"
                    )
                }
            }
        }

        if (showTextDialog) {
            Surface(
                modifier = Modifier
                    .width(280.dp)
                    .padding(8.dp),
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                shadowElevation = 8.dp,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(
                        text = "输入要分析的文本",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = plainTextInput,
                        onValueChange = { plainTextInput = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp)
                            .onFocusChanged { forceState ->
                                if (forceState.isFocused) {
                                    onFocusChange(true)
                                }
                            },
                        placeholder = { Text("在此粘贴或输入内容...", fontSize = 14.sp) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        )
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = {
                                showTextDialog = false
                                plainTextInput = ""
                                onFocusChange(false)
                            }
                        ) {
                            Text("取消")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (plainTextInput.isNotBlank()) {
                                    val intent = Intent(context, RequestActivity::class.java).apply {
                                        putExtra("link_data", plainTextInput)
                                        putExtra("type", SRC_TYPE.TEXT.name)
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    context.startActivity(intent)
                                    showTextDialog = false
                                    plainTextInput = ""
                                    isExpanded = false
                                    onExpandChange(false)
                                    onFocusChange(false)
                                }
                            },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("分析")
                        }
                    }
                }
            }
        }
    }
}
