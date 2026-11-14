package me.andannn.kio.chat.widget

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kio.chat.domain.RoomJoinRequest

@Composable
internal fun RoomJoinRequestsDialog(
    requests: List<RoomJoinRequest>,
    onDismiss: () -> Unit,
    onApprove: (Long) -> Unit,
    onReject: (Long) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("入群申请（${requests.size}）") },
        text = {
            if (requests.isEmpty()) Text("还没有新的入群申请")
            else LazyColumn(Modifier.fillMaxWidth().heightIn(max = 480.dp)) {
                items(requests, key = { it.id }) { request ->
                    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                        Text(request.username, style = MaterialTheme.typography.titleSmall)
                        Text("申请加入「${request.roomName}」", style = MaterialTheme.typography.bodyMedium)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = { onReject(request.id) }) {
                                Text("拒绝")
                            }
                            TextButton(onClick = { onApprove(request.id) }) {
                                Text("同意")
                            }
                        }
                        HorizontalDivider()
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}

@Composable
internal fun JoinRoomDialog(
    onDismiss: () -> Unit,
    onSubmit: (Long) -> Unit,
) {
    var roomId by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("加入聊天室") },
        text = {
            OutlinedTextField(
                value = roomId,
                onValueChange = { roomId = it },
                label = { Text("要加入的房间 ID") },
                supportingText = { Text("输入房间 ID，向群主发送入群申请") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        },
        confirmButton = {
            TextButton(onClick = { roomId.toLongOrNull()?.let(onSubmit) }, enabled = (roomId.toLongOrNull() ?: 0) > 0) {
                Text("发送申请")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
