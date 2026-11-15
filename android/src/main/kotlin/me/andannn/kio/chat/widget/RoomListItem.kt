package me.andannn.kio.chat.widget

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kio.chat.domain.Message
import kio.chat.domain.Room
import kotlinx.coroutines.flow.filterNotNull
import kio.chat.domain.User
import me.andannn.kio.chat.ui.theme.KioChatTheme
import me.andannn.kio.chat.Navigator
import me.andannn.kio.chat.Screen
import me.andannn.kio.chat.UserRepository
import me.andannn.kio.chat.util.RetainedPresenter
import me.andannn.kio.chat.util.retainPresenter
import me.andannn.kio.chat.util.stateInRetainedModel
import org.koin.mp.KoinPlatform.getKoin

@Composable
internal fun RoomListItem(navigator: Navigator, room: Room, modifier: Modifier = Modifier) {
    val state = retainRoomPreviewPresenter(navigator, room).present()
    RoomListItemContent(
        room = state.room,
        previewMessage = state.previewMessage,
        modifier = modifier,
        onClick = {
            state.eventSink.invoke(RoomPreviewUiEvent.OnRoomClick)
        },
    )
}

sealed interface RoomPreviewUiEvent {
    data object OnRoomClick : RoomPreviewUiEvent
}

@Stable
private data class RoomPreviewState(
    val room: Room,
    val previewMessage: Message?,
    val eventSink: (RoomPreviewUiEvent) -> Unit,
)

private class RoomPreviewPresenter(
    private val navigator: Navigator,
    room: Room,
    repository: UserRepository
): RetainedPresenter<RoomPreviewState>() {
    private val previewMessageFlow = repository.observeLastMessage(room.id).stateInRetainedModel(null)
    private val roomStateFlow = repository.observeRoomById(roomId = room.id).filterNotNull().stateInRetainedModel(room)

    @Composable
    override fun present(): RoomPreviewState {
        val roomState by roomStateFlow.collectAsStateWithLifecycle()
        val previewMessage by previewMessageFlow.collectAsStateWithLifecycle()
        return RoomPreviewState(
            roomState,
            previewMessage = previewMessage
        ) { event ->
            when (event) {
                is RoomPreviewUiEvent.OnRoomClick -> navigator.navigateTo(Screen.RoomChat(roomState))
            }
        }
    }
}

@Composable
private fun retainRoomPreviewPresenter(
    navigator: Navigator,
    room: Room,
    repository: UserRepository = getKoin().get(),
) = retainPresenter(navigator,room, repository) {
    RoomPreviewPresenter(navigator, room, repository)
}

@Composable
fun RoomListItemContent(
    room: Room,
    modifier: Modifier = Modifier,
    previewMessage: Message?,
    onClick: () -> Unit = {},
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        ListItem(
            modifier = Modifier.padding(vertical = 8.dp),
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            leadingContent = {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ) {
                    Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.Forum, contentDescription = null)
                    }
                }
            },
            headlineContent = {
                Text(
                    room.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            supportingContent = {
                Text(
                    text = previewMessage?.let { "${it.nickname}：${it.text}" } ?: "还没有消息",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            trailingContent = {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun RoomListItemPreview() {
    val user = User(1, "AA", "")
    KioChatTheme(dynamicColor = false) {
        Surface {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                RoomListItemContent(
                    room = Room(1, "日常交流"),
                    previewMessage = Message(1, user, "晚上一起吃饭吗？", user.username),
                )
                RoomListItemContent(
                    room = Room(2, "一个比较长的聊天室名称，用来查看省略效果"),
                    previewMessage = Message(2, user, "这是一条比较长的消息，用来查看最新消息预览的单行省略效果。", user.username),
                )
                RoomListItemContent(room = Room(3, "新的聊天室"), previewMessage = null)
            }
        }
    }
}
