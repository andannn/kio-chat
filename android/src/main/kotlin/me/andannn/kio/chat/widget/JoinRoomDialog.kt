package me.andannn.kio.chat.widget

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.text.input.KeyboardType

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
