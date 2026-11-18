package me.andannn.kio.chat.widget

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
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
import me.andannn.kio.chat.MainNavigator
import me.andannn.kio.chat.MainScreen
import me.andannn.kio.chat.LocalVoiceCallRepository
import me.andannn.kio.chat.LocalUserRepository
import me.andannn.kio.chat.UserRepository
import me.andannn.kio.chat.util.RetainedPresenter
import me.andannn.kio.chat.util.retainPresenter
import me.andannn.kio.chat.util.stateInRetainedModel

@Composable
internal fun RoomListItem(navigator: MainNavigator, room: Room, modifier: Modifier = Modifier) {
    val state = retainRoomPreviewPresenter(navigator, room).present()
    val voiceCall by LocalVoiceCallRepository.current.callState.collectAsStateWithLifecycle()
    RoomListItemContent(
        room = state.room,
        isInCall = voiceCall.roomId == state.room.id,
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
    val eventSink: (RoomPreviewUiEvent) -> Unit,
)

private class RoomPreviewPresenter(
    private val navigator: MainNavigator,
    room: Room,
    repository: UserRepository
): RetainedPresenter<RoomPreviewState>() {
    private val roomStateFlow = repository.observeRoomById(roomId = room.id).filterNotNull().stateInRetainedModel(room)

    @Composable
    override fun present(): RoomPreviewState {
        val roomState by roomStateFlow.collectAsStateWithLifecycle()
        return RoomPreviewState(
            roomState,
        ) { event ->
            when (event) {
                is RoomPreviewUiEvent.OnRoomClick -> navigator.navigateTo(MainScreen.RoomChat(roomState))
            }
        }
    }
}

@Composable
private fun retainRoomPreviewPresenter(
    navigator: MainNavigator,
    room: Room,
    repository: UserRepository = LocalUserRepository.current,
) = retainPresenter(navigator,room, repository) {
    RoomPreviewPresenter(navigator, room, repository)
}

@Composable
fun RoomListItemContent(
    room: Room,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
    isInCall: Boolean = false,
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
            trailingContent = if (isInCall) {
                {
                    Icon(
                        imageVector = Icons.Filled.Call,
                        contentDescription = "正在通话",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            } else null,
            headlineContent = {
                Text(
                    room.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun RoomListItemPreview() {
    KioChatTheme(dynamicColor = false) {
        Surface {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                RoomListItemContent(
                    room = Room(1, "日常交流"),
                    isInCall = true,
                )
                RoomListItemContent(
                    room = Room(2, "一个比较长的聊天室名称，用来查看省略效果"),
                )
                RoomListItemContent(room = Room(3, "新的聊天室"))
            }
        }
    }
}
