package me.andannn.kio.chat

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import kio.chat.domain.Room
import kio.chat.domain.RoomRole
import kio.chat.domain.RoomMember
import kotlinx.coroutines.launch
import me.andannn.kio.chat.ui.theme.KioChatTheme
import me.andannn.kio.chat.util.LocalSnackBarController
import me.andannn.kio.chat.util.Presenter
import me.andannn.kio.chat.util.RetainedPresenter
import me.andannn.kio.chat.util.SnackBarController
import me.andannn.kio.chat.util.rememberAndSetupSnackBarHostState
import me.andannn.kio.chat.util.retainPresenter
import org.koin.mp.KoinPlatform.getKoin

fun EntryProviderScope<NavKey>.roomMembersEntry(navigator: Navigator) {
    entry<Screen.RoomMembers> { screen -> RoomMembers(navigator, screen.room) }
}

@Composable
private fun retainRoomMembersState(
    navigator: Navigator,
    room: Room,
    service: ChatService = getKoin().get(),
    snackBarController: SnackBarController = LocalSnackBarController.current,
) = retainPresenter(navigator, room.id, service, snackBarController) {
    RoomMembersLayoutPresenter(navigator, room, service, snackBarController)
}

sealed interface RoomMembersUiEvent {
    data object OnBackClick : RoomMembersUiEvent
}

private data class RoomMembersLayoutState(
    val roomName: String,
    val members: List<RoomMember>,
    val eventSink: (RoomMembersUiEvent) -> Unit,
)

private class RoomMembersLayoutPresenter(
    val navigator: Navigator,
    val room: Room,
    val service: ChatService,
    val snackBarController: SnackBarController,
) : RetainedPresenter<RoomMembersLayoutState>() {
    private var members by mutableStateOf<List<RoomMember>>(emptyList())

    init {
        retainedScope.launch {
            val (value, error) = service.getRoomMembers(room.id)
            context(navigator, snackBarController) {
                if (error != null) handleError(error)
                else members = value.orEmpty()
            }
        }
    }

    @Composable
    override fun present() = RoomMembersLayoutState(room.name, members) { event ->
        when (event) {
            RoomMembersUiEvent.OnBackClick -> navigator.popBackStack()
        }
    }
}

@Composable
private fun RoomMembers(
    navigator: Navigator,
    room: Room,
    modifier: Modifier = Modifier,
    presenter: Presenter<RoomMembersLayoutState> = retainRoomMembersState(navigator, room),
) {
    val state = presenter.present()
    RoomMembersContent(
        roomName = state.roomName,
        members = state.members,
        modifier = modifier,
        snackbarHostState = rememberAndSetupSnackBarHostState(),
        onBack = { state.eventSink(RoomMembersUiEvent.OnBackClick) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RoomMembersContent(
    roomName: String,
    members: List<RoomMember>,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    onBack: () -> Unit = {},
) {
    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("房间成员")
                        Text(roomName, style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding),
        ) {
            items(members, key = { it.userId }) { member ->
                ListItem(
                    headlineContent = { Text(member.username) },
                    supportingContent = {
                        Text(when (member.role) {
                            RoomRole.Owner -> "群主"
                            RoomRole.Member -> "成员"
                        })
                    },
                )
            }
        }
    }
}

@Preview
@Composable
private fun RoomMembersPreview() {
    KioChatTheme(dynamicColor = false) {
        RoomMembersContent("日常交流", listOf(RoomMember(1, "小明"), RoomMember(2, "小红")))
    }
}
