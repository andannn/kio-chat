package me.andannn.kio.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import kio.chat.domain.Room
import kotlinx.coroutines.launch
import me.andannn.kio.chat.ui.theme.KioChatTheme
import me.andannn.kio.chat.util.LocalSnackBarController
import me.andannn.kio.chat.util.Presenter
import me.andannn.kio.chat.util.RetainedPresenter
import me.andannn.kio.chat.util.SnackBarController
import me.andannn.kio.chat.util.rememberAndSetupSnackBarHostState
import me.andannn.kio.chat.util.retainPresenter
import org.koin.mp.KoinPlatform.getKoin

fun EntryProviderScope<NavKey>.roomsEntry(navigator: Navigator) {
    entry<Screen.RoomList> { Rooms(navigator) }
}

@Composable
private fun retainRoomsState(
    navigator: Navigator,
    service: ChatService = getKoin().get(),
    snackBarController: SnackBarController = LocalSnackBarController.current,
) = retainPresenter(navigator, service, snackBarController) {
    RoomsLayoutPresenter(navigator, service, snackBarController)
}

sealed interface RoomsUiEvent {
    data class OnRoomClick(val room: Room) : RoomsUiEvent
    data object OnLogoutClick : RoomsUiEvent
}

private data class RoomsLayoutState(
    val rooms: List<Room>,
    val eventSink: (RoomsUiEvent) -> Unit,
)

private class RoomsLayoutPresenter(
    val navigator: Navigator,
    val service: ChatService,
    val snackBarController: SnackBarController,
) : RetainedPresenter<RoomsLayoutState>() {
    private var rooms by mutableStateOf<List<Room>>(emptyList())

    init {
        retainedScope.launch {
            val (value, error) = service.getRoomList()
            context(navigator, snackBarController) {
                if (error != null) handleError(error)
                else rooms = value.orEmpty()
            }
        }
    }

    context(navigator: Navigator, snackBar: SnackBarController)
    private fun logout() {
        retainedScope.launch {
            val (_, error) = service.logout()
            if (error != null) handleError(error)
            else navigator.clearTopAndGoToLogin()
        }
    }

    @Composable
    override fun present(): RoomsLayoutState {
        context(navigator, snackBarController) {
            return RoomsLayoutState(rooms) { event ->
                when (event) {
                    is RoomsUiEvent.OnRoomClick -> navigator.navigateTo(Screen.RoomChat(event.room))
                    RoomsUiEvent.OnLogoutClick -> logout()
                }
            }
        }
    }
}

@Composable
private fun Rooms(
    navigator: Navigator,
    modifier: Modifier = Modifier,
    presenter: Presenter<RoomsLayoutState> = retainRoomsState(navigator),
) {
    val state = presenter.present()
    RoomsContent(
        rooms = state.rooms,
        modifier = modifier,
        snackbarHostState = rememberAndSetupSnackBarHostState(),
        onLogout = { state.eventSink(RoomsUiEvent.OnLogoutClick) },
        onRoomClick = { state.eventSink(RoomsUiEvent.OnRoomClick(it)) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RoomsContent(
    rooms: List<Room>,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    onLogout: () -> Unit = {},
    onRoomClick: (Room) -> Unit = {},
) {
    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Kio Chat") },
                actions = {
                    TextButton(onClick = onLogout) { Text("退出登录") }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding),
        ) {
            items(rooms, key = { it.id }) { room ->
                ListItem(
                    headlineContent = { Text(room.name) },
                    modifier = Modifier.clickable { onRoomClick(room) },
                )
            }
        }
    }
}

@Preview
@Composable
private fun RoomsPreview() {
    KioChatTheme(dynamicColor = false) {
        RoomsContent(listOf(Room(1, "公共聊天室"), Room(2, "日常交流")))
    }
}
