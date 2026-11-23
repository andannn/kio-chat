package me.andannn.kio.chat.widget

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kio.chat.domain.RoomMember
import me.andannn.kio.chat.VoiceCallState
import me.andannn.kio.chat.ui.theme.KioChatTheme

/**
 * 语音通话气泡面板（Bubble）
 * 包含通话状态、音频设备、参会成员头像列表以及开启/加入、静音、挂断控制操作。
 */
@Composable
fun VoiceCallBubble(
    roomId: Long,
    participants: List<RoomMember>,
    call: VoiceCallState,
    isBusy: Boolean,
    onToggleMuted: () -> Unit,
    onJoinVoice: () -> Unit,
    onLeaveVoice: () -> Unit,
    modifier: Modifier = Modifier,
    roomName: String? = null,
    onClick: (() -> Unit)? = null,
) {
    val joined = call.roomId == roomId

    Surface(
        onClick = { onClick?.invoke() },
        enabled = onClick != null,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = if (joined) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
               else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        border = BorderStroke(
            width = 1.dp,
            color = if (joined) MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                    else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
        ),
        shadowElevation = if (onClick != null) 4.dp else 0.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(
                                if (joined) Color(0xFF4CAF50) // 活跃绿点
                                else if (participants.isNotEmpty()) Color(0xFF81C784)
                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            )
                    )
                    val baseTitle = if (!roomName.isNullOrBlank()) "$roomName · " else ""
                    val titleText = when {
                        joined -> "${baseTitle}正在通话 (${participants.size}人)"
                        participants.isNotEmpty() -> "${baseTitle}通话中 (${participants.size}人)"
                        else -> "${baseTitle}语音通话（无人）"
                    }
                    Text(
                        text = titleText,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = if (joined) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (joined && !call.audioDeviceName.isNullOrBlank()) {
                        Text(
                            text = "· ${call.audioDeviceName}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                Spacer(Modifier.width(8.dp))

                if (joined) {
                    // 通话中控制按钮组：静音与挂断
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilledTonalIconButton(
                            onClick = onToggleMuted,
                            enabled = !isBusy,
                            modifier = Modifier.size(34.dp),
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = if (call.muted) MaterialTheme.colorScheme.errorContainer
                                                else MaterialTheme.colorScheme.surfaceContainerHighest,
                                contentColor = if (call.muted) MaterialTheme.colorScheme.error
                                               else MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                        ) {
                            Icon(
                                imageVector = if (call.muted) Icons.Filled.MicOff else Icons.Filled.Mic,
                                contentDescription = if (call.muted) "取消静音" else "静音",
                                modifier = Modifier.size(18.dp),
                            )
                        }

                        FilledIconButton(
                            onClick = onLeaveVoice,
                            enabled = !isBusy,
                            modifier = Modifier.size(34.dp),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError,
                            ),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.CallEnd,
                                contentDescription = "挂断",
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                } else {
                    // 未加入时快速加入按钮
                    FilledTonalButton(
                        onClick = onJoinVoice,
                        enabled = !isBusy,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    ) {
                        Icon(
                            Icons.Filled.Call,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = if (participants.isEmpty()) "开启语音" else "加入通话",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }

            if (participants.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))

                // 通话成员横向头像列表
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    participants.forEach { member ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            UserAvatar(
                                name = member.nickname,
                                key = member.userId,
                                size = 42.dp,
                                modifier = if (joined) {
                                    Modifier.border(2.dp, Color(0xFF4CAF50), CircleShape)
                                } else {
                                    Modifier
                                }
                            )

                            Text(
                                text = member.nickname,
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(max = 52.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun VoiceCallBubbleEmptyPreview() {
    KioChatTheme(dynamicColor = false) {
        Box(Modifier.padding(16.dp)) {
            VoiceCallBubble(
                roomId = 1L,
                participants = emptyList(),
                call = VoiceCallState(),
                isBusy = false,
                onToggleMuted = {},
                onJoinVoice = {},
                onLeaveVoice = {},
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun VoiceCallBubbleActiveOthersPreview() {
    KioChatTheme(dynamicColor = false) {
        Box(Modifier.padding(16.dp)) {
            VoiceCallBubble(
                roomId = 1L,
                participants = listOf(
                    RoomMember(userId = 1L, nickname = "小明"),
                    RoomMember(userId = 2L, nickname = "Alice"),
                    RoomMember(userId = 3L, nickname = "Bob"),
                ),
                call = VoiceCallState(),
                isBusy = false,
                onToggleMuted = {},
                onJoinVoice = {},
                onLeaveVoice = {},
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun VoiceCallBubbleJoinedPreview() {
    KioChatTheme(dynamicColor = false) {
        Box(Modifier.padding(16.dp)) {
            VoiceCallBubble(
                roomId = 1L,
                participants = listOf(
                    RoomMember(userId = 100L, nickname = "小李"),
                    RoomMember(userId = 1L, nickname = "小明"),
                    RoomMember(userId = 2L, nickname = "Alice"),
                ),
                call = VoiceCallState(
                    roomId = 1L,
                    muted = false,
                    audioDeviceName = "蓝牙耳机",
                ),
                isBusy = false,
                onToggleMuted = {},
                onJoinVoice = {},
                onLeaveVoice = {},
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun VoiceCallBubbleJoinedMutedPreview() {
    KioChatTheme(dynamicColor = false) {
        Box(Modifier.padding(16.dp)) {
            VoiceCallBubble(
                roomId = 1L,
                participants = listOf(
                    RoomMember(userId = 100L, nickname = "小李"),
                    RoomMember(userId = 1L, nickname = "小明"),
                ),
                call = VoiceCallState(
                    roomId = 1L,
                    muted = true,
                    audioDeviceName = "内置扬声器",
                ),
                isBusy = false,
                onToggleMuted = {},
                onJoinVoice = {},
                onLeaveVoice = {},
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun VoiceCallBubbleWithRoomNamePreview() {
    KioChatTheme(dynamicColor = false) {
        Box(Modifier.padding(16.dp)) {
            VoiceCallBubble(
                roomId = 1L,
                roomName = "Room Name",
                participants = listOf(
                    RoomMember(userId = 100L, nickname = "我自己"),
                    RoomMember(userId = 1L, nickname = "小明"),
                ),
                call = VoiceCallState(
                    roomId = 1L,
                    muted = true,
                    audioDeviceName = "内置扬声器",
                ),
                isBusy = false,
                onToggleMuted = {},
                onJoinVoice = {},
                onLeaveVoice = {},
            )
        }
    }
}
