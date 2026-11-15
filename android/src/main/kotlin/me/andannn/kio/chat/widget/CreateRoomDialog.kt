package me.andannn.kio.chat.widget

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier

@Composable
internal fun CreateRoomDialog(onDismiss: () -> Unit, onSubmit: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("创建聊天室") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("聊天室名称") },
                placeholder = { Text("给聊天室起个名字") },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = { onSubmit(name.trim()) }, enabled = name.isNotBlank()) { Text("创建") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
