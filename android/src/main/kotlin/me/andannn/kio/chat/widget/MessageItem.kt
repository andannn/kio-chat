package me.andannn.kio.chat.widget

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kio.chat.domain.Message
import kio.chat.domain.RoomRole

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
        Row(
            modifier = Modifier
                .align(if (isMine) Alignment.CenterEnd else Alignment.CenterStart)
                .widthIn(max = maxWidth * 0.88f),
            horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start,
            verticalAlignment = Alignment.Top,
        ) {
            if (!isMine) {
                UserAvatar(
                    name = message.nickname,
                    key = message.member.userId,
                    size = 38.dp,
                    modifier = Modifier.padding(top = 4.dp, end = 8.dp),
                )
            }

            Column(
                modifier = Modifier.weight(1f, fill = false),
                horizontalAlignment = if (isMine) Alignment.End else Alignment.Start,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (isMine) {
                        if (message.member.role == RoomRole.Owner) {
                            RoomRoleBadge("群主")
                        }
                        Text(
                            text = message.nickname,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Text(
                            text = message.nickname,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (message.member.role == RoomRole.Owner) {
                            RoomRoleBadge("群主")
                        }
                    }
                }
                Box {
                    Surface(
                        shape = RoundedCornerShape(
                            topStart = if (isMine) 20.dp else 4.dp,
                            topEnd = if (isMine) 4.dp else 20.dp,
                            bottomStart = 20.dp,
                            bottomEnd = 20.dp,
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
                                .defaultMinSize(minHeight = 44.dp)
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                        )
                    }
                }
            }

            if (isMine) {
                UserAvatar(
                    name = message.nickname,
                    key = message.member.userId,
                    size = 38.dp,
                    modifier = Modifier.padding(top = 4.dp, start = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun RoomRoleBadge(
    roleText: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(4.dp),
        color = Color(0x26FFB400), // rgba(255, 180, 0, 0.15)
        border = BorderStroke(1.dp, Color(0x4DFFB400)), // rgba(255, 180, 0, 0.3)
    ) {
        Text(
            text = roleText,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 10.sp,
                lineHeight = 14.sp,
                color = Color(0xFFF6AD55),
            ),
        )
    }
}
