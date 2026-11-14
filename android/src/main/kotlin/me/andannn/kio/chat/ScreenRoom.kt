package me.andannn.kio.chat

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import me.andannn.kio.chat.ui.theme.KioChatTheme
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import kio.chat.domain.Message
import kio.chat.domain.Room as ChatRoom
import kio.chat.domain.User
import kio.chat.domain.MessageEvent
import kotlinx.coroutines.launch
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.collectLatest
import me.andannn.kio.chat.util.LocalSnackBarController
import me.andannn.kio.chat.util.SnackBarController
import me.andannn.kio.chat.util.rememberAndSetupSnackBarHostState
import org.koin.mp.KoinPlatform.getKoin
import me.andannn.kio.chat.util.Presenter
import me.andannn.kio.chat.util.RetainedPresenter
import me.andannn.kio.chat.util.retainPresenter
import me.andannn.kio.chat.widget.MessageItem

private const val TAG = "ScreenRoom"
fun EntryProviderScope<NavKey>.roomEntry(navigator: Navigator) {
    entry<Screen.RoomChat> { screen ->
        Room(navigator, screen.room)
    }
}

@Composable
private fun retainRoomState(
    navigator: Navigator,
    room: ChatRoom,
    service: ChatService = getKoin().get(),
    snackBarController: SnackBarController = LocalSnackBarController.current,
) = retainPresenter(navigator, room.id, service, snackBarController) {
    RoomLayoutPresenter(navigator, room.id, service, snackBarController)
}

sealed interface RoomUiEvent {
    data class OnMessageChanged(val value: String) : RoomUiEvent
    data class OnUpdateMessage(val messageId: Long, val content: String) : RoomUiEvent
    data class OnDeleteMessage(val messageId: Long) : RoomUiEvent
    data object OnLoadHistory : RoomUiEvent
    data object OnBackClick : RoomUiEvent
    data object OnSendClick : RoomUiEvent
}

private data class RoomLayoutState(
    val messages: List<Message> = emptyList(),
    val userId: Long? = null,
    val hasMoreHistory: Boolean = false,
    val isLoadingHistory: Boolean = false,
    val effects: Flow<RoomEffect> = emptyFlow(),
    val message: String = "",
    val eventSink: (RoomUiEvent) -> Unit = {},
)

private sealed interface RoomEffect {
    data class ScrollToBottom(val animated: Boolean) : RoomEffect
}

private class RoomLayoutPresenter(
    val navigator: Navigator,
    val roomId: Long,
    val service: ChatService,
    val snackBarController: SnackBarController,
) : RetainedPresenter<RoomLayoutState>() {

    private val effectChannel = Channel<RoomEffect>(Channel.BUFFERED)
    private val effects = effectChannel.receiveAsFlow()
    var messages = mutableStateListOf<Message>()
    var userId by mutableStateOf<Long?>(null)
    var currentInput by mutableStateOf("")
    var hasMoreHistory by mutableStateOf(false)
    var isLoadingHistory by mutableStateOf(false)
    private var historyBeforeId: Long? = null

    init {
        retainedScope.launch {
            val error = service.observeMessageEvents(roomId) { pack ->
                Log.d(TAG, "observeMessageEvents: pack=${pack}")
                userId = pack.userId
                when (val event = pack.event) {
                    is MessageEvent.Initial -> {
                        historyBeforeId = event.message.minOfOrNull { it.id }
                        hasMoreHistory = event.message.size >= 50
                        messages.addAll(event.message.reversed())
                        effectChannel.send(RoomEffect.ScrollToBottom(animated = false))
                    }
                    is MessageEvent.Added -> {
                        messages.add(0, event.message)
                        effectChannel.send(RoomEffect.ScrollToBottom(animated = true))
                    }
                    is MessageEvent.Changed -> {
                        val index = messages.indexOfFirst { it.id == event.message.id }
                        if (index >= 0) {
                            messages[index] = event.message
                        }
                    }
                    is MessageEvent.Deleted -> {
                        messages.removeIf { it.id == event.messageId }
                    }
                }
            }
            context(navigator, snackBarController) {
                if (error != null) handleError(error = error)
            }
        }.invokeOnCompletion {
            Log.d(TAG, "observeMessageEvents: finished")
        }
    }

    context(navigator: Navigator, snackBar: SnackBarController)
    private fun loadHistory() {
        val beforeId = historyBeforeId ?: return
        if (isLoadingHistory || !hasMoreHistory) return

        retainedScope.launch {
            isLoadingHistory = true
            val (page, error) = service.getMessageHistory(roomId, beforeId)
            isLoadingHistory = false

            if (error != null) {
                handleError(error)
                return@launch
            }

            if (page != null) {
                messages.addAll(page.messages.asReversed())
                historyBeforeId = page.messages.minOfOrNull { it.id } ?: beforeId
                hasMoreHistory = page.hasMore
            }
        }
    }

    context(navigator: Navigator, snackBar: SnackBarController)
    private fun sendMessage() {
        val content = currentInput.trim()
        if (content.isNotEmpty() && content.length <= 2000) {
            retainedScope.launch {
                currentInput = ""
                val (_, error) = service.sendMessage(roomId, content)
                if (error != null) {
                    currentInput = content
                    handleError(error)
                }
            }
        }
    }

    context(navigator: Navigator, snackBar: SnackBarController)
    private fun updateMessage(messageId: Long, content: String) {
        val text = content.trim()
        if (text.isEmpty() || text.length > 2000) return
        retainedScope.launch {
            val (_, error) = service.updateMessage(roomId, messageId, text)
            if (error != null) handleError(error)
        }
    }

    context(navigator: Navigator, snackBar: SnackBarController)
    private fun deleteMessage(messageId: Long) {
        retainedScope.launch {
            val (_, error) = service.deleteMessage(roomId, messageId)
            if (error != null) handleError(error)
        }
    }

    @Composable
    override fun present(): RoomLayoutState {
        context(navigator, snackBarController) {
            return RoomLayoutState(
                messages = messages,
                userId = userId,
                hasMoreHistory = hasMoreHistory,
                isLoadingHistory = isLoadingHistory,
                effects = effects,
                message = currentInput,
            ) { event ->
                when (event) {
                    is RoomUiEvent.OnMessageChanged -> currentInput = event.value
                    is RoomUiEvent.OnUpdateMessage -> updateMessage(event.messageId, event.content)
                    is RoomUiEvent.OnDeleteMessage -> deleteMessage(event.messageId)
                    RoomUiEvent.OnLoadHistory -> loadHistory()
                    RoomUiEvent.OnBackClick -> navigator.popBackStack()
                    RoomUiEvent.OnSendClick -> sendMessage()
                }
            }
        }
    }
}

@Composable
private fun Room(
    navigator: Navigator,
    room: ChatRoom,
    modifier: Modifier = Modifier,
    presenter: Presenter<RoomLayoutState> = retainRoomState(navigator, room),
) {
    val state = presenter.present()
    val listState = rememberLazyListState()
    val currentMessages by rememberUpdatedState(state.messages)
    LaunchedEffect(state.effects, listState) {
        state.effects.collectLatest { effect ->
            when (effect) {
                is RoomEffect.ScrollToBottom -> {
                    // Let the list receive the updated message state before scrolling.
                    withFrameNanos { }
                    if (currentMessages.isNotEmpty()) {
                        if (effect.animated) listState.animateScrollToItem(0)
                        else listState.scrollToItem(0)
                    }
                }
            }
        }
    }
    RoomContent(
        roomName = room.name,
        messages = state.messages,
        message = state.message,
        currentUserId = state.userId,
        hasMoreHistory = state.hasMoreHistory,
        isLoadingHistory = state.isLoadingHistory,
        onLoadHistory = { state.eventSink(RoomUiEvent.OnLoadHistory) },
        modifier = modifier,
        listState = listState,
        snackbarHostState = rememberAndSetupSnackBarHostState(),
        onMessageChanged = { state.eventSink(RoomUiEvent.OnMessageChanged(it)) },
        onUpdateMessage = { id, content -> state.eventSink(RoomUiEvent.OnUpdateMessage(id, content)) },
        onDeleteMessage = { state.eventSink(RoomUiEvent.OnDeleteMessage(it)) },
        onSend = { state.eventSink(RoomUiEvent.OnSendClick) },
        onBack = { state.eventSink(RoomUiEvent.OnBackClick) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RoomContent(
    roomName: String,
    messages: List<Message>,
    message: String,
    modifier: Modifier = Modifier,
    currentUserId: Long? = null,
    hasMoreHistory: Boolean = false,
    isLoadingHistory: Boolean = false,
    onLoadHistory: () -> Unit = {},
    listState: LazyListState = rememberLazyListState(),
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    onMessageChanged: (String) -> Unit = {},
    onSend: () -> Unit = {},
    onBack: () -> Unit = {},
    onUpdateMessage: (Long, String) -> Unit = { _, _ -> },
    onDeleteMessage: (Long) -> Unit = {},
) {
    Scaffold(
        modifier = modifier.imePadding(),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(roomName) },
                navigationIcon = {
                    IconButton(onClick = {
                        onBack()
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                        )
                    }
                },
            )
        },
        bottomBar = {
            BottomAppBar(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
                OutlinedTextField(
                    value = message,
                    onValueChange = onMessageChanged,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("输入消息…") },
                    shape = MaterialTheme.shapes.extraLarge,
                    maxLines = 4,
                )
                Spacer(Modifier.width(12.dp))
                Button(onClick = onSend) { Text("发送") }
            }
        },
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding),
            reverseLayout = true,
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(messages, key = { it.id }) { message ->
                val isMine = currentUserId != null && message.user.id == currentUserId
                MessageItem(
                    modifier = Modifier.animateItem(),
                    message = message,
                    isMine = isMine,
                    onDelete = { onDeleteMessage(message.id) },
                    onEdit = { onUpdateMessage(message.id, it) },
                )
            }
            if (hasMoreHistory) {
                item(key = "load-history") {
                    TextButton(
                        onClick = onLoadHistory,
                        enabled = !isLoadingHistory,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (isLoadingHistory) "加载中…" else "加载更早的消息")
                    }
                }
            }
        }
    }
}

@Preview
@Composable
private fun RoomPreview() {
    val alice = User(id = 1, username = "小明", passwordHash = "")
    val bob = User(id = 2, username = "小红", passwordHash = "")
    val messages = listOf(
        Message(1, alice, "你好，欢迎来到 Kio Chat！", alice.username),
        Message(2, bob, "小明，你好 👋", bob.username),
        Message(3, alice, "这是一条比较长的消息，用来预览正文自动换行的效果。", alice.username),
        Message(4, bob, "也可以发送多行文字。\n这是第二行。", bob.username),
    )
    KioChatTheme(dynamicColor = false) {
        RoomContent("Title", messages = messages, message = "正在输入消息…", currentUserId = alice.id)
    }
}
