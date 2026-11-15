package me.andannn.kio.chat

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import kio.chat.domain.Room
import kio.chat.domain.RoomRole
import kio.chat.domain.RoomMember
import kotlinx.coroutines.launch
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import me.andannn.kio.chat.ui.theme.KioChatTheme
import me.andannn.kio.chat.util.LocalSnackBarController
import me.andannn.kio.chat.util.Presenter
import me.andannn.kio.chat.util.RetainedPresenter
import me.andannn.kio.chat.util.SnackBarController
import me.andannn.kio.chat.util.rememberAndSetupSnackBarHostState
import me.andannn.kio.chat.util.retainPresenter
import me.andannn.kio.chat.util.stateInRetainedModel
import org.koin.mp.KoinPlatform.getKoin

fun EntryProviderScope<NavKey>.roomSettingEntry(navigator: Navigator) {
    entry<Screen.RoomSetting> { screen -> RoomSetting(navigator, screen.room) }
}

@Composable
private fun retainRoomSettingState(
    navigator: Navigator,
    room: Room,
    service: ChatService = getKoin().get(),
    repository: UserRepository = getKoin().get(),
    snackBarController: SnackBarController = LocalSnackBarController.current,
) = retainPresenter(navigator, room.id, service, repository, snackBarController) {
    RoomSettingLayoutPresenter(navigator, room, service, repository, snackBarController)
}

sealed interface RoomSettingUiEvent {
    data class OnRenameRoom(val name: String) : RoomSettingUiEvent
    data object OnDisbandRoom : RoomSettingUiEvent
    data object OnBackClick : RoomSettingUiEvent
}

private sealed interface RoomSettingEffect {
    data object NameSaved : RoomSettingEffect
}

private data class RoomSettingLayoutState(
    val room: Room,
    val members: List<RoomMember>,
    val isOwner: Boolean,
    val effects: Flow<RoomSettingEffect>,
    val eventSink: (RoomSettingUiEvent) -> Unit,
)

private class RoomSettingLayoutPresenter(
    val navigator: Navigator,
    val room: Room,
    val service: ChatService,
    repository: UserRepository,
    val snackBarController: SnackBarController,
) : RetainedPresenter<RoomSettingLayoutState>() {
    private val roomFlow = repository.observeRoomById(room.id)
        .onEach { if (it == null) navigator.clearTopAndGoToHome() }
        .filterNotNull()
        .stateInRetainedModel(room)
    private var members by mutableStateOf<List<RoomMember>>(emptyList())
    private var userId by mutableStateOf<Long?>(null)
    private var renaming = false
    private var disbanding = false
    private val effectChannel = Channel<RoomSettingEffect>(Channel.BUFFERED)
    private val effects = effectChannel.receiveAsFlow()

    init {
        retainedScope.launch {
            val (value, error) = service.getRoomMembers(room.id)
            context(navigator, snackBarController) {
                if (error != null) {
                    handleError(error)
                } else if (value != null) {
                    members = value.members
                    userId = value.userId
                }
            }
        }
    }

    private fun renameRoom(name: String) {
        val value = name.trim()
        if (value.isEmpty() || renaming) return
        renaming = true
        retainedScope.launch {
            try {
                val (_, error) = service.renameRoom(room.id, value)
                context(navigator, snackBarController) {
                    if (error != null) handleError(error)
                    else effectChannel.send(RoomSettingEffect.NameSaved)
                }
            } finally {
                renaming = false
            }
        }
    }

    private fun disbandRoom() {
        if (disbanding) return
        disbanding = true
        retainedScope.launch {
            try {
                val (_, error) = service.disbandRoom(room.id)
                context(navigator, snackBarController) {
                    if (error != null) handleError(error)
                }
            } finally {
                disbanding = false
            }
        }
    }

    @Composable
    override fun present(): RoomSettingLayoutState {
        val currentRoom by roomFlow.collectAsStateWithLifecycle()
        return RoomSettingLayoutState(
            room = currentRoom,
            members = members,
            isOwner = members.any { it.userId == userId && it.role == RoomRole.Owner },
            effects = effects,
        ) { event ->
            when (event) {
                is RoomSettingUiEvent.OnRenameRoom -> renameRoom(event.name)
                RoomSettingUiEvent.OnDisbandRoom -> disbandRoom()
                RoomSettingUiEvent.OnBackClick -> navigator.popBackStack()
            }
        }
    }
}

@Composable
private fun RoomSetting(
    navigator: Navigator,
    room: Room,
    modifier: Modifier = Modifier,
    presenter: Presenter<RoomSettingLayoutState> = retainRoomSettingState(navigator, room),
) {
    val state = presenter.present()
    var showRename by remember { mutableStateOf(false) }
    var confirmDisband by remember { mutableStateOf(false) }
    LaunchedEffect(state.effects) {
        state.effects.collect { effect ->
            when (effect) {
                RoomSettingEffect.NameSaved -> showRename = false
            }
        }
    }
    RoomSettingContent(
        room = state.room,
        isOwner = state.isOwner,
        onRename = { showRename = true },
        onDisband = { confirmDisband = true },
        members = state.members,
        modifier = modifier,
        snackbarHostState = rememberAndSetupSnackBarHostState(),
        onBack = { state.eventSink(RoomSettingUiEvent.OnBackClick) },
    )
    if (showRename && state.isOwner) {
        RoomNameDialog(
            roomName = state.room.name,
            onDismiss = { showRename = false },
            onSubmit = { state.eventSink(RoomSettingUiEvent.OnRenameRoom(it)) },
        )
    }
    if (confirmDisband && state.isOwner) {
        AlertDialog(
            onDismissRequest = { confirmDisband = false },
            title = { Text("解散聊天室？") },
            text = { Text("解散「${state.room.name}」后，所有成员将被移出，聊天记录会被删除，无法恢复。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDisband = false
                        state.eventSink(RoomSettingUiEvent.OnDisbandRoom)
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("解散") }
            },
            dismissButton = { TextButton(onClick = { confirmDisband = false }) { Text("取消") } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RoomSettingContent(
    room: Room,
    members: List<RoomMember>,
    isOwner: Boolean = false,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    onBack: () -> Unit = {},
    onRename: () -> Unit = {},
    onDisband: () -> Unit = {},
) {
    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("房间设置")
                        Text(room.name, style = MaterialTheme.typography.bodyMedium,
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
            contentPadding = PaddingValues(16.dp),
        ) {
            item {
                Text(
                    "房间信息",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, bottom = 12.dp),
                )
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                ) {
                    ListItem(
                        headlineContent = { Text("聊天室名称") },
                        supportingContent = { Text(room.name) },
                        trailingContent = {
                            if (isOwner) Icon(Icons.Outlined.Edit, contentDescription = "修改名称")
                        },
                        modifier = Modifier.clickable(enabled = isOwner, onClick = onRename),
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    )
                    ListItem(
                        headlineContent = { Text("房间 ID") },
                        supportingContent = { Text(room.id.toString()) },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    )
                }
                Text(
                    "成员（${members.size}）",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, top = 24.dp, bottom = 8.dp),
                )
            }
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
            if (isOwner) {
                item {
                    OutlinedButton(
                        onClick = onDisband,
                        modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                    ) { Text("解散聊天室") }
                }
            }
        }
    }
}

@Preview
@Composable
private fun RoomSettingPreview() {
    KioChatTheme(dynamicColor = false) {
        RoomSettingContent(Room(1, "日常交流"), listOf(RoomMember(1, "小明", RoomRole.Owner), RoomMember(2, "小红")), isOwner = true)
    }
}

@Composable
private fun RoomNameDialog(roomName: String, onDismiss: () -> Unit, onSubmit: (String) -> Unit) {
    var name by remember { mutableStateOf(roomName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("修改聊天室名称") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("聊天室名称") },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = { onSubmit(name.trim()) }, enabled = name.isNotBlank()) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
