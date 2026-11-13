package me.andannn.kio.chat.widget

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import kio.chat.domain.Message

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MessageItem(
    message: Message,
    isMine: Boolean,
    modifier: Modifier = Modifier,
    onDelete: () -> Unit = {},
    onEdit: (String) -> Unit = {},
) {
    var showActions by remember(message.id) { mutableStateOf(false) }
    var confirmDelete by remember(message.id) { mutableStateOf(false) }
    var showEditor by remember(message.id) { mutableStateOf(false) }
    var editText by remember(message.id) { mutableStateOf("") }
    val haptics = LocalHapticFeedback.current

    if (isMine && showActions) {
        ModalBottomSheet(onDismissRequest = { showActions = false }) {
            Text(
                text = "消息操作",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            TextButton(
                onClick = {
                    showActions = false
                    editText = message.text
                    showEditor = true
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Text("编辑消息", modifier = Modifier.padding(vertical = 8.dp))
            }
            TextButton(
                onClick = {
                    showActions = false
                    confirmDelete = true
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Text("删除消息", modifier = Modifier.padding(vertical = 8.dp))
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    if (isMine && showEditor) {
        AlertDialog(
            onDismissRequest = { showEditor = false },
            title = { Text("编辑消息") },
            text = {
                OutlinedTextField(
                    value = editText,
                    onValueChange = { editText = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("消息内容") },
                    minLines = 3,
                    maxLines = 6,
                    isError = editText.trim().length > 2000,
                )
            },
            confirmButton = {
                TextButton(
                    enabled = editText.trim().isNotEmpty() && editText.trim().length <= 2000,
                    onClick = {
                        showEditor = false
                        onEdit(editText.trim())
                    },
                ) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { showEditor = false }) { Text("取消") }
            },
        )
    }

    if (isMine && confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("删除消息") },
            text = { Text("确定删除这条消息吗？此操作无法撤销。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        onDelete()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("取消") }
            },
        )
    }

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .align(if (isMine) Alignment.CenterEnd else Alignment.CenterStart)
                .widthIn(max = maxWidth * 0.84f),
            horizontalAlignment = if (isMine) Alignment.End else Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (!isMine) {
                Text(
                    text = message.username,
                    modifier = Modifier.padding(horizontal = 12.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box {
                Surface(
                    shape = RoundedCornerShape(
                        topStart = 20.dp,
                        topEnd = 20.dp,
                        bottomStart = if (isMine) 20.dp else 6.dp,
                        bottomEnd = if (isMine) 6.dp else 20.dp,
                    ),
                    color = if (isMine) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = if (isMine) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurface,
                ) {
                    Text(
                        text = message.text,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier
                            .combinedClickable(
                                enabled = isMine,
                                onClick = {},
                                onLongClickLabel = "消息操作",
                                onLongClick = {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    showActions = true
                                },
                            )
                            .defaultMinSize(minHeight = 48.dp)
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                }

            }
        }
    }
}
