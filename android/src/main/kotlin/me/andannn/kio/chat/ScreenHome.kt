package me.andannn.kio.chat

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.GroupAdd
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import kio.chat.domain.Room
import kio.chat.domain.RoomJoinRequest
import me.andannn.kio.chat.widget.JoinRoomDialog
import me.andannn.kio.chat.widget.RoomListItem
import kotlinx.coroutines.launch
import me.andannn.kio.chat.widget.CreateRoomDialog
import me.andannn.kio.chat.ui.theme.KioChatTheme
import me.andannn.kio.chat.util.LocalSnackBarController
import me.andannn.kio.chat.util.Presenter
import me.andannn.kio.chat.util.RetainedPresenter
import me.andannn.kio.chat.util.SnackBarController
import me.andannn.kio.chat.util.rememberAndSetupSnackBarHostState
import me.andannn.kio.chat.util.retainPresenter
import me.andannn.kio.chat.util.stateInRetainedModel
import me.andannn.kio.chat.widget.RoomListItemContent
import org.koin.mp.KoinPlatform.getKoin

fun EntryProviderScope<NavKey>.homeEntry(navigator: Navigator) {
    entry<Screen.Home> { Home(navigator) }
}
private const val TAG = "ScreenHome"
@Composable
private fun retainHomeState(
    navigator: Navigator,
    service: ChatService = getKoin().get(),
    repository: UserRepository = getKoin().get(),
    snackBarController: SnackBarController = LocalSnackBarController.current,
) = retainPresenter(navigator, repository, service, snackBarController) {
    HomeLayoutPresenter(navigator, repository, service, snackBarController)
}

sealed interface HomeUiEvent {
    data class OnCreateRoom(val name: String) : HomeUiEvent
    data class OnSubmitJoinRequest(val roomId: Long) : HomeUiEvent
    data object OnJoinRequestsClick : HomeUiEvent
    data object OnLogoutClick : HomeUiEvent
}

private data class HomeLayoutState(
    val rooms: List<Room>,
    val requests: List<RoomJoinRequest>,
    val eventSink: (HomeUiEvent) -> Unit,
)

private class HomeLayoutPresenter(
    val navigator: Navigator,
    val repository: UserRepository,
    val service: ChatService,
    val snackBarController: SnackBarController,
) : RetainedPresenter<HomeLayoutState>() {
    private val rooms = repository.observeRooms().stateInRetainedModel(emptyList())
    private val requests = repository.observeRoomJoinRequest().stateInRetainedModel(emptyList())
    private var submitting = false
    private var creating = false

    init {
        retainedScope.launch {
            val error = repository.observingUserEvent()
            context(navigator, snackBarController) {
                if (error != null) handleError(error)
            }
        }
    }

    context(navigator: Navigator, snackBar: SnackBarController)
    private fun createRoom(name: String) {
        val value = name.trim()
        if (value.isEmpty() || creating) return
        creating = true
        retainedScope.launch {
            try {
                val (_, error) = service.createRoom(value)
                if (error != null) handleError(error)
            } finally {
                creating = false
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
    private fun logout() {
        retainedScope.launch {
            val (_, error) = service.logout()
            if (error != null) handleError(error)
            else navigator.clearTopAndGoToLogin()
        }
    }

    @Composable
    override fun present(): HomeLayoutState {
        val roomsState by rooms.collectAsStateWithLifecycle()
        val requestsState by requests.collectAsStateWithLifecycle()
        context(navigator, snackBarController) {
            return HomeLayoutState(
                rooms = roomsState,
                requests = requestsState,
            ) { event ->
                when (event) {
                    is HomeUiEvent.OnCreateRoom -> createRoom(event.name)
                    is HomeUiEvent.OnSubmitJoinRequest -> submitJoinRequest(event.roomId)
                    HomeUiEvent.OnJoinRequestsClick -> navigator.navigateTo(Screen.RoomJoinRequests)
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
    var showJoinRoom by remember { mutableStateOf(false) }
    var showCreateRoom by remember { mutableStateOf(false) }
    HomeContent(
        rooms = state.rooms,
        pendingCount = state.requests.size,
        modifier = modifier,
        snackbarHostState = rememberAndSetupSnackBarHostState(),
        onLogout = { state.eventSink(HomeUiEvent.OnLogoutClick) },
        onShowRequests = { state.eventSink(HomeUiEvent.OnJoinRequestsClick) },
        onShowJoinRoom = { showJoinRoom = true },
        onShowCreateRoom = { showCreateRoom = true },
        roomItem = { room ->
            RoomListItem(navigator = navigator, room = room)
        }
    )
    if (showCreateRoom) {
        CreateRoomDialog(
            onDismiss = { showCreateRoom = false },
            onSubmit = {
                showCreateRoom = false
                state.eventSink(HomeUiEvent.OnCreateRoom(it))
            },
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
    pendingCount: Int = 0,
    onShowRequests: () -> Unit = {},
    onShowJoinRoom: () -> Unit = {},
    onShowCreateRoom: () -> Unit = {},
    roomItem: @Composable (Room) -> Unit
) {
    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilledTonalButton(onClick = onShowCreateRoom, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                        Text("创建聊天室")
                    }
                    OutlinedButton(onClick = onShowJoinRoom, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Outlined.GroupAdd, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                        Text("加入聊天室")
                    }
                }
            }
            if (rooms.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 64.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            Icons.Outlined.Forum,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text("还没有聊天室", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "加入一个房间，开始聊天吧",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                item {
                    Text(
                        "我的聊天室 · ${rooms.size}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                    )
                }
                items(rooms, key = { it.id }) { room ->
                    roomItem(room)
                }
            }
        }
    }
}

@Preview
@Composable
private fun HomePreview() {
    KioChatTheme(dynamicColor = false) {
        HomeContent(listOf(Room(1, "公共聊天室"), Room(2, "日常交流"))) {
            RoomListItemContent(it, previewMessage = null)
        }
    }
}
