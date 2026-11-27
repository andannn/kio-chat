package me.andannn.kio.chat.widget

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import kio.chat.domain.Message
import kio.chat.domain.MessageExtra
import kio.chat.domain.MessageType
import kio.chat.domain.RoomMember
import kio.chat.domain.RoomRole
import me.andannn.kio.chat.BuildConfig
import me.andannn.kio.chat.ui.theme.KioChatTheme
import timber.log.Timber

private const val MAX_MESSAGE_LENGTH = 2000

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

    if (isMine && showActions) {
        MessageActionsSheet(
            canEdit = message.extra == null,
            onEditClick = {
                showActions = false
                showEditor = true
            },
            onDeleteClick = {
                showActions = false
                confirmDelete = true
            },
            onDismiss = { showActions = false },
        )
    }

    if (isMine && showEditor) {
        EditMessageDialog(
            initialText = message.text,
            onConfirm = { newText ->
                showEditor = false
                onEdit(newText)
            },
            onDismiss = { showEditor = false },
        )
    }

    if (isMine && confirmDelete) {
        DeleteMessageDialog(
            onConfirm = {
                confirmDelete = false
                onDelete()
            },
            onDismiss = { confirmDelete = false },
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
                MessageSenderHeader(
                    nickname = message.nickname,
                    role = message.member.role,
                    isMine = isMine,
                )
                MessageBubble(
                    message = message,
                    isMine = isMine,
                    onLongClick = { showActions = true },
                )
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MessageActionsSheet(
    canEdit: Boolean,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            text = "消息操作",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        TextButton(
            onClick = onEditClick,
            enabled = canEdit,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text("编辑消息", modifier = Modifier.padding(vertical = 8.dp))
        }
        TextButton(
            onClick = onDeleteClick,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
        ) {
            Text("删除消息", modifier = Modifier.padding(vertical = 8.dp))
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun EditMessageDialog(
    initialText: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var editText by remember(initialText) { mutableStateOf(initialText) }
    val trimmed = editText.trim()
    val isValid = trimmed.isNotEmpty() && trimmed.length <= MAX_MESSAGE_LENGTH

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑消息") },
        text = {
            OutlinedTextField(
                value = editText,
                onValueChange = { editText = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("消息内容") },
                minLines = 3,
                maxLines = 6,
                isError = trimmed.length > MAX_MESSAGE_LENGTH,
            )
        },
        confirmButton = {
            TextButton(
                enabled = isValid,
                onClick = { onConfirm(trimmed) },
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

@Composable
private fun DeleteMessageDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("删除消息") },
        text = { Text("确定删除这条消息吗？此操作无法撤销。") },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) { Text("删除") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

@Composable
private fun MessageSenderHeader(
    nickname: String,
    role: RoomRole,
    isMine: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (isMine && role == RoomRole.Owner) {
            RoomRoleBadge("群主")
        }
        Text(
            text = nickname,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!isMine && role == RoomRole.Owner) {
            RoomRoleBadge("群主")
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

@Composable
private fun MessageBubble(
    message: Message,
    isMine: Boolean,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val longPressModifier = Modifier.combinedClickable(
        enabled = isMine,
        onClick = {},
        onLongClickLabel = "消息操作",
        onLongClick = {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            onLongClick()
        },
    )

    Box(modifier = modifier) {
        Surface(
            shape = RoundedCornerShape(
                topStart = if (isMine) 20.dp else 4.dp,
                topEnd = if (isMine) 4.dp else 20.dp,
                bottomStart = 20.dp,
                bottomEnd = 20.dp,
            ),
            color = if (message.extra is MessageExtra.Image) Color.Transparent
            else if (isMine) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = if (isMine) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurface,
        ) {
            when (val extra = message.extra) {
                is MessageExtra.Image -> MessageImageContent(
                    extra = extra,
                    isMine = isMine,
                    modifier = longPressModifier,
                )
                is MessageExtra.Voice -> MessageTextContent(
                    text = message.text,
                    modifier = longPressModifier,
                )
                null -> MessageTextContent(
                    text = message.text,
                    modifier = longPressModifier,
                )
            }
        }
    }
}

@Composable
private fun MessageImageContent(
    extra: MessageExtra.Image,
    isMine: Boolean,
    modifier: Modifier = Modifier,
) {
    val fullUrl = "${BuildConfig.BASE_URL}/${extra.url.trimStart('/')}"
    val ratio = if (extra.width > 0 && extra.height > 0) {
        extra.width.toFloat() / extra.height.toFloat()
    } else 1f

    AsyncImage(
        model = fullUrl,
        contentDescription = "图片",
        modifier = modifier
            .widthIn(max = 240.dp)
            .heightIn(max = 320.dp)
            .aspectRatio(ratio)
            .clip(
                RoundedCornerShape(
                    topStart = if (isMine) 16.dp else 4.dp,
                    topEnd = if (isMine) 4.dp else 16.dp,
                    bottomStart = 16.dp,
                    bottomEnd = 16.dp,
                )
            ),
        contentScale = ContentScale.Crop,
    )
}

@Composable
private fun MessageTextContent(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        modifier = modifier
            .defaultMinSize(minHeight = 44.dp)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    )
}

@Preview(showBackground = true)
@Composable
private fun MessageItemOtherTextPreview() {
    KioChatTheme(dynamicColor = false) {
        Surface {
            MessageItem(
                message = Message(
                    id = 1L,
                    member = RoomMember(userId = 1L, nickname = "Alice", role = RoomRole.Owner),
                    text = "大家好，欢迎来到聊天室！",
                    nickname = "Alice",
                ),
                isMine = false,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun MessageItemMineTextPreview() {
    KioChatTheme(dynamicColor = false) {
        Surface {
            MessageItem(
                message = Message(
                    id = 2L,
                    member = RoomMember(userId = 3L, nickname = "我", role = RoomRole.Member),
                    text = "这是一条稍微长一点的文本消息，用来预览多行换行和气泡最大宽度的显示效果。",
                    nickname = "我",
                ),
                isMine = true,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun MessageItemOtherImagePreview() {
    KioChatTheme(dynamicColor = false) {
        Surface {
            MessageItem(
                message = Message(
                    id = 3L,
                    member = RoomMember(userId = 1L, nickname = "Alice", role = RoomRole.Member),
                    text = "",
                    nickname = "Alice",
                    type = MessageType.Image,
                    extra = MessageExtra.Image(
                        url = "https://example.com/sample.jpg",
                        width = 800,
                        height = 600,
                    ),
                ),
                isMine = false,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun MessageItemMineImagePreview() {
    KioChatTheme(dynamicColor = false) {
        Surface {
            MessageItem(
                message = Message(
                    id = 4L,
                    member = RoomMember(userId = 3L, nickname = "我", role = RoomRole.Member),
                    text = "",
                    nickname = "我",
                    type = MessageType.Image,
                    extra = MessageExtra.Image(
                        url = "https://example.com/sample2.jpg",
                        width = 600,
                        height = 800,
                    ),
                ),
                isMine = true,
            )
        }
    }
}
