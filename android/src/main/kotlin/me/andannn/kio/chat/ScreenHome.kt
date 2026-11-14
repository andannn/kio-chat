package me.andannn.kio.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import kio.chat.domain.Room
import kio.chat.domain.RoomJoinRequest
import kio.chat.domain.RoomJoinRequestEvent
import me.andannn.kio.chat.widget.RoomJoinRequestsDialog
import me.andannn.kio.chat.widget.JoinRoomDialog
import kotlinx.coroutines.launch
import me.andannn.kio.chat.ui.theme.KioChatTheme
import me.andannn.kio.chat.util.LocalSnackBarController
import me.andannn.kio.chat.util.Presenter
import me.andannn.kio.chat.util.RetainedPresenter
import me.andannn.kio.chat.util.SnackBarController
import me.andannn.kio.chat.util.rememberAndSetupSnackBarHostState
import me.andannn.kio.chat.util.retainPresenter
import org.koin.mp.KoinPlatform.getKoin

fun EntryProviderScope<NavKey>.homeEntry(navigator: Navigator) {
    entry<Screen.Home> { Home(navigator) }
}

@Composable
private fun retainHomeState(
    navigator: Navigator,
    service: ChatService = getKoin().get(),
    snackBarController: SnackBarController = LocalSnackBarController.current,
) = retainPresenter(navigator, service, snackBarController) {
    HomeLayoutPresenter(navigator, service, snackBarController)
}

sealed interface HomeUiEvent {
    data class OnRoomClick(val room: Room) : HomeUiEvent
    data class OnSubmitJoinRequest(val roomId: Long) : HomeUiEvent
    data class OnApproveRequest(val requestId: Long) : HomeUiEvent
    data class OnRejectRequest(val requestId: Long) : HomeUiEvent
    data object OnLogoutClick : HomeUiEvent
}

private data class HomeLayoutState(
    val rooms: List<Room>,
    val requests: List<RoomJoinRequest>,
    val eventSink: (HomeUiEvent) -> Unit,
)

private class HomeLayoutPresenter(
    val navigator: Navigator,
    val service: ChatService,
    val snackBarController: SnackBarController,
) : RetainedPresenter<HomeLayoutState>() {
    private var rooms by mutableStateOf<List<Room>>(emptyList())
    private val requests = mutableStateListOf<RoomJoinRequest>()
    private var submitting = false

    init {
        retainedScope.launch {
            val (value, error) = service.getRoomList()
            context(navigator, snackBarController) {
                if (error != null) handleError(error)
                else rooms = value.orEmpty()
            }
        }
    }

    init {
        retainedScope.launch {
            val error = service.observeRoomJoinRequestEvents { event ->
                when (event) {
                    is RoomJoinRequestEvent.Initial -> {
                        requests.clear()
                        requests.addAll(event.requests)
                    }
                    is RoomJoinRequestEvent.Added -> {
                        requests.add(event.request)
                    }
                    is RoomJoinRequestEvent.Removed -> requests.removeAll { it.id == event.requestId }
                }
            }
            context(navigator, snackBarController) {
                if (error != null) handleError(error)
            }
        }
    }

    context(navigator: Navigator, snackBar: SnackBarController)
    private fun submitJoinRequest(roomId: Long) {
        if (roomId <= 0) return
        if (submitting) return
        submitting = true
        retainedScope.launch {
            try {
                val (_, error) = service.createRoomJoinRequest(roomId)
                if (error != null) handleError(error)
                else {
                    snackBar.showSnackBar("申请已发送，等群主同意就能加入啦。")
                }
            } finally {
                submitting = false
            }
        }
    }

    context(navigator: Navigator, snackBar: SnackBarController)
    private fun reviewRequest(requestId: Long, approve: Boolean) {
        retainedScope.launch {
            val (_, error) = if (approve) {
                service.approveRoomJoinRequest(requestId)
            }
            else {
                service.rejectRoomJoinRequest(requestId)
            }
            if (error != null) handleError(error)
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
    override fun present(): HomeLayoutState {
        context(navigator, snackBarController) {
            return HomeLayoutState(
                rooms = rooms,
                requests = requests,
            ) { event ->
                when (event) {
                    is HomeUiEvent.OnRoomClick -> navigator.navigateTo(Screen.RoomChat(event.room))
                    is HomeUiEvent.OnSubmitJoinRequest -> submitJoinRequest(event.roomId)
                    is HomeUiEvent.OnApproveRequest -> reviewRequest(event.requestId, approve = true)
                    is HomeUiEvent.OnRejectRequest -> reviewRequest(event.requestId, approve = false)
                    HomeUiEvent.OnLogoutClick -> logout()
                }
            }
        }
    }
}

@Composable
private fun Home(
    navigator: Navigator,
    modifier: Modifier = Modifier,
    presenter: Presenter<HomeLayoutState> = retainHomeState(navigator),
) {
    val state = presenter.present()
    var showRequests by remember { mutableStateOf(false) }
    var showJoinRoom by remember { mutableStateOf(false) }
    HomeContent(
        rooms = state.rooms,
        pendingCount = state.requests.size,
        onShowRequests = { showRequests = true },
        onShowJoinRoom = { showJoinRoom = true },
        modifier = modifier,
        snackbarHostState = rememberAndSetupSnackBarHostState(),
        onLogout = { state.eventSink(HomeUiEvent.OnLogoutClick) },
        onRoomClick = { state.eventSink(HomeUiEvent.OnRoomClick(it)) },
    )
    if (showRequests) {
        RoomJoinRequestsDialog(
            requests = state.requests,
            onDismiss = { showRequests = false },
            onApprove = { state.eventSink(HomeUiEvent.OnApproveRequest(it)) },
            onReject = { state.eventSink(HomeUiEvent.OnRejectRequest(it)) },
        )
    }
    if (showJoinRoom) {
        JoinRoomDialog(
            onDismiss = { showJoinRoom = false },
            onSubmit = { roomId ->
                state.eventSink(HomeUiEvent.OnSubmitJoinRequest(roomId))
                showJoinRoom = false
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeContent(
    rooms: List<Room>,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    onLogout: () -> Unit = {},
    onRoomClick: (Room) -> Unit = {},
    pendingCount: Int = 0,
    onShowRequests: () -> Unit = {},
    onShowJoinRoom: () -> Unit = {},
) {
    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onShowJoinRoom,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("加入聊天室") },
            )
        },
        topBar = {
            TopAppBar(
                title = { Text("Kio Chat") },
                actions = {
                    IconButton(onClick = onShowRequests) {
                        BadgedBox(
                            badge = {
                                if (pendingCount > 0) {
                                    Badge(
                                        containerColor = MaterialTheme.colorScheme.error,
                                        contentColor = MaterialTheme.colorScheme.onError,
                                    ) {
                                        Text(if (pendingCount > 99) "99+" else pendingCount.toString())
                                    }
                                }
                            },
                        ) {
                            Icon(
                                Icons.Default.Notifications,
                                contentDescription = if (pendingCount > 0) "入群申请，$pendingCount 条待处理" else "入群申请",
                            )
                        }
                    }
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
private fun HomePreview() {
    KioChatTheme(dynamicColor = false) {
        HomeContent(listOf(Room(1, "公共聊天室"), Room(2, "日常交流")))
    }
}
