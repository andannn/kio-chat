package me.andannn.kio.chat

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.material.icons.filled.Image
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Headset
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
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
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.unit.sp
import me.andannn.kio.chat.ui.theme.KioChatTheme
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import kio.chat.domain.Message
import kio.chat.domain.Room
import kio.chat.domain.RoomMember
import kio.chat.domain.UserChatEvent
import kotlinx.coroutines.launch
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.onSubscription
import me.andannn.kio.chat.util.LocalSnackBarController
import me.andannn.kio.chat.util.SnackBarController
import me.andannn.kio.chat.util.rememberAndSetupSnackBarHostState
import org.koin.mp.KoinPlatform.getKoin
import me.andannn.kio.chat.util.Presenter
import me.andannn.kio.chat.util.RetainedPresenter
import me.andannn.kio.chat.util.retainPresenter
import me.andannn.kio.chat.util.stateInRetainedModel
import me.andannn.kio.chat.widget.UserAvatar
import me.andannn.kio.chat.widget.VoiceCallBubble
import me.andannn.kio.chat.widget.MessageItem

private const val TAG = "ScreenRoom"

fun EntryProviderScope<NavKey>.roomEntry(rootNavigator: RootNavigator, navigator: MainNavigator) {
    entry<MainScreen.RoomChat> { screen ->
        RoomUi(rootNavigator, navigator, screen.room)
    }
}

@Composable
private fun retainRoomState(
    rootNavigator: RootNavigator,
    navigator: MainNavigator,
    room: Room,
    service: ChatService = getKoin().get(),
    userEventSource: UserEventSource = getKoin().get(),
    repository: UserRepository = getKoin().get(),
    snackBarController: SnackBarController = LocalSnackBarController.current,
    voiceRepository: VoiceCallRepository = getKoin().get(),
) = retainPresenter(rootNavigator, navigator, room.id, service, userEventSource, repository, snackBarController, voiceRepository) {
    RoomLayoutPresenter(rootNavigator, navigator, room, service, userEventSource, repository, snackBarController, voiceRepository)
}

sealed interface RoomUiEvent {
    data class OnMessageChanged(val value: String) : RoomUiEvent
    data class OnUpdateMessage(val messageId: Long, val content: String) : RoomUiEvent
    data class OnDeleteMessage(val messageId: Long) : RoomUiEvent
    data object OnLoadHistory : RoomUiEvent
    data object OnSettingClick : RoomUiEvent
    data object OnBackClick : RoomUiEvent
    data object OnSendClick : RoomUiEvent
    data object OnJoinVoice : RoomUiEvent
    data object OnLeaveVoice : RoomUiEvent
    data object OnToggleMuted : RoomUiEvent
    data object OnMicrophoneDenied : RoomUiEvent
    data class OnSendImage(val bytes: ByteArray, val width: Int, val height: Int) : RoomUiEvent
}

private data class RoomLayoutState(
    val room: Room,
    val messages: List<Message> = emptyList(),
    val userId: Long? = null,
    val hasMoreHistory: Boolean = false,
    val isLoadingHistory: Boolean = false,
    val effects: Flow<RoomEffect> = emptyFlow(),
    val message: String = "",
    val voiceParticipants: List<RoomMember> = emptyList(),
    val voiceCall: VoiceCallState = VoiceCallState(),
    val isVoiceBusy: Boolean = false,
    val eventSink: (RoomUiEvent) -> Unit = {},
)

private sealed interface RoomEffect {
    data class ScrollToBottom(val animated: Boolean) : RoomEffect
}

private class RoomLayoutPresenter(
    val rootNavigator: RootNavigator,
    val navigator: MainNavigator,
    room: Room,
    val service: ChatService,
    val eventSource: UserEventSource,
    repository: UserRepository,
    val snackBarController: SnackBarController,
    private val voiceRepository: VoiceCallRepository,
) : RetainedPresenter<RoomLayoutState>() {

    private val roomId = room.id

    private val roomFlow = repository.observeRoomChangedById(room.id)
        .onEach { if (it == null) navigator.clearTopAndGoToHome() }
        .filterNotNull()
        .stateInRetainedModel(room)
    private val effectChannel = Channel<RoomEffect>(Channel.BUFFERED)
    private val effects = effectChannel.receiveAsFlow()
    var messages = mutableStateListOf<Message>()
    var userId by mutableStateOf<Long?>(null)
    var currentInput by mutableStateOf("")
    var hasMoreHistory by mutableStateOf(false)
    var isLoadingHistory by mutableStateOf(false)
    private val historyBeforeId: Long? get() = messages.minOfOrNull { it.id }
    private val voiceParticipantsFlow = repository.observeVoiceParticipants(roomId, ::handleApiError)
        .stateInRetainedModel(emptyList())

    private var isVoiceBusy by mutableStateOf(false)

    init {
        context(rootNavigator, snackBarController) {
            init()
        }
    }

    context(_: RootNavigator, _: SnackBarController)
    private fun init() {
        retainedScope.launch {
            eventSource.events
                .onSubscription {
                    val (page, error) = service.getMessageHistory(roomId, beforeId = Long.MAX_VALUE)
                    if (error != null) {
                        handleError(error)
                        return@onSubscription
                    }

                    userId = page!!.userId
                    messages.addAll(page.messages.sortedByDescending { it.id })
                    hasMoreHistory = page.hasMore
                }
                .filterIsInstance<UserChatEvent>()
                .collect { event ->
                    when (event) {
                        is UserChatEvent.Added -> {
                            if (messages.none { it.id == event.message.id }) {
                                messages.add(0, event.message)
                                effectChannel.send(RoomEffect.ScrollToBottom(animated = true))
                            }
                        }
                        is UserChatEvent.Changed -> {
                            val index = messages.indexOfFirst { it.id == event.message.id }
                            if (index != -1) messages[index] = event.message
                        }
                        is UserChatEvent.Deleted -> {
                            messages.removeIf { it.id == event.messageId }
                        }
                    }
                }
        }
    }

    context(_: RootNavigator, _: SnackBarController)
    private fun changeVoice(action: suspend () -> ApiError?) {
        if (isVoiceBusy) return
        isVoiceBusy = true
        retainedScope.launch {
            val error = try { action() } finally { isVoiceBusy = false }
            if (error != null) handleError(error)
        }
    }

    context(_: RootNavigator, _: SnackBarController)
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
                hasMoreHistory = page.hasMore
            }
        }
    }

    context(_: RootNavigator, _: SnackBarController)
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

    context(_: RootNavigator, _: SnackBarController)
    private fun sendImage(bytes: ByteArray, width: Int, height: Int) {
        retainedScope.launch {
            val (_, error) = service.sendImageMessage(roomId, bytes, width, height)
            if (error != null) {
                handleError(error)
            }
        }
    }

    context(_: RootNavigator, _: SnackBarController)
    private fun updateMessage(messageId: Long, content: String) {
        val text = content.trim()
        if (text.isEmpty()) return
        retainedScope.launch {
            val (_, error) = service.updateMessage(roomId, messageId, text)
            if (error != null) handleError(error)
        }
    }

    context(_: RootNavigator, _: SnackBarController)
    private fun deleteMessage(messageId: Long) {
        retainedScope.launch {
            val (_, error) = service.deleteMessage(roomId, messageId)
            if (error != null) handleError(error)
        }
    }

    @Composable
    override fun present(): RoomLayoutState {
        val roomState by roomFlow.collectAsStateWithLifecycle()
        val voiceCall by voiceRepository.callState.collectAsStateWithLifecycle()
        val voiceParticipants by voiceParticipantsFlow.collectAsStateWithLifecycle()
        context(rootNavigator, snackBarController) {
            return RoomLayoutState(
                room = roomState,
                messages = messages,
                userId = userId,
                hasMoreHistory = hasMoreHistory,
                isLoadingHistory = isLoadingHistory,
                effects = effects,
                message = currentInput,
                voiceParticipants = voiceParticipants,
                voiceCall = voiceCall,
                isVoiceBusy = isVoiceBusy,
            ) { event ->
                when (event) {
                    is RoomUiEvent.OnMessageChanged -> currentInput = event.value
                    is RoomUiEvent.OnUpdateMessage -> updateMessage(event.messageId, event.content)
                    is RoomUiEvent.OnDeleteMessage -> deleteMessage(event.messageId)
                    RoomUiEvent.OnLoadHistory -> loadHistory()
                    RoomUiEvent.OnSettingClick -> navigator.navigateTo(MainScreen.RoomSetting(roomState))
                    RoomUiEvent.OnBackClick -> navigator.popBackStack()
                    RoomUiEvent.OnSendClick -> sendMessage()
                    is RoomUiEvent.OnSendImage -> sendImage(event.bytes, event.width, event.height)
                    RoomUiEvent.OnJoinVoice -> changeVoice { voiceRepository.join(roomId) }
                    RoomUiEvent.OnLeaveVoice -> changeVoice { voiceRepository.leave() }
                    RoomUiEvent.OnToggleMuted -> changeVoice { voiceRepository.setMuted(!voiceRepository.callState.value.muted) }
                    RoomUiEvent.OnMicrophoneDenied -> retainedScope.launch {
                        handleError(ApiError.ServerError("需要麦克风权限才能加入语音，请在系统设置中允许。"))
                    }
                }
            }
        }
    }

    private suspend fun handleApiError(error: ApiError) {
        context(rootNavigator, snackBarController) {
            handleError(error)
        }
    }
}

@Composable
private fun RoomUi(
    rootNavigator: RootNavigator,
    navigator: MainNavigator,
    room: Room,
    modifier: Modifier = Modifier,
    presenter: Presenter<RoomLayoutState> = retainRoomState(rootNavigator, navigator, room),
) {
    val state = presenter.present()
    val context = LocalContext.current
    val currentEventSink by rememberUpdatedState(state.eventSink)
    val voicePermissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
        val audioGranted = results[Manifest.permission.RECORD_AUDIO] == true
        currentEventSink(if (audioGranted) RoomUiEvent.OnJoinVoice else RoomUiEvent.OnMicrophoneDenied)
    }
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
        room = state.room,
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
        onSettingClick = { state.eventSink(RoomUiEvent.OnSettingClick) },
        voiceParticipants = state.voiceParticipants,
        voiceCall = state.voiceCall,
        isVoiceBusy = state.isVoiceBusy,
        onJoinVoice = {
            val missingPermissions = context.getMissingVoiceCallPermissions()
            if (missingPermissions.isEmpty()) {
                state.eventSink(RoomUiEvent.OnJoinVoice)
            } else {
                if (Manifest.permission.RECORD_AUDIO !in missingPermissions) {
                    // 已有麦克风权限（仅缺少可选的蓝牙权限），不阻塞加入语音，同时发起蓝牙权限申请
                    state.eventSink(RoomUiEvent.OnJoinVoice)
                }
                voicePermissions.launch(missingPermissions.toTypedArray())
            }
        },
        onLeaveVoice = { state.eventSink(RoomUiEvent.OnLeaveVoice) },
        onToggleMuted = { state.eventSink(RoomUiEvent.OnToggleMuted) },
        onSendImage = { bytes, w, h -> state.eventSink(RoomUiEvent.OnSendImage(bytes, w, h)) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RoomContent(
    room: Room,
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
    onSendImage: (ByteArray, Int, Int) -> Unit = { _, _, _ -> },
    onBack: () -> Unit = {},
    onSettingClick: () -> Unit = {},
    onUpdateMessage: (Long, String) -> Unit = { _, _ -> },
    onDeleteMessage: (Long) -> Unit = {},
    voiceParticipants: List<RoomMember> = emptyList(),
    voiceCall: VoiceCallState = VoiceCallState(),
    isVoiceBusy: Boolean = false,
    onJoinVoice: () -> Unit = {},
    onLeaveVoice: () -> Unit = {},
    onToggleMuted: () -> Unit = {},
) {
    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(room.name)
                        Text(
                            "房间 ID：${room.id}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = { TextButton(onClick = onSettingClick) { Text("设置") } },
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
            Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(BottomAppBarDefaults.windowInsets.union(WindowInsets.ime))
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    // 语音通话常驻气泡面板（统一承载通话状态、成员列表、静音、开启与挂断功能）
                    VoiceCallBubble(
                        roomId = room.id,
                        participants = voiceParticipants,
                        call = voiceCall,
                        isBusy = isVoiceBusy,
                        onToggleMuted = onToggleMuted,
                        onJoinVoice = onJoinVoice,
                        onLeaveVoice = onLeaveVoice,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                    Row(
                        modifier = Modifier.padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val context = LocalContext.current
                        val photoPickerLauncher = rememberLauncherForActivityResult(
                            contract = ActivityResultContracts.PickVisualMedia()
                        ) { uri: Uri? ->
                            if (uri != null) {
                                val cr = context.contentResolver
                                val (w, h) = try {
                                    cr.openInputStream(uri)?.use { stream ->
                                        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                                        BitmapFactory.decodeStream(stream, null, options)
                                        options.outWidth to options.outHeight
                                    } ?: (0 to 0)
                                } catch (_: Exception) {
                                    0 to 0
                                }
                                val bytes = try {
                                    cr.openInputStream(uri)?.use { it.readBytes() }
                                } catch (_: Exception) {
                                    null
                                }
                                if (bytes != null && bytes.isNotEmpty()) {
                                    onSendImage(bytes, w, h)
                                }
                            }
                        }

                        IconButton(
                            onClick = {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            modifier = Modifier.padding(end = 4.dp)
                        ) {
                            Icon(
                                Icons.Default.Image,
                                contentDescription = "发送图片",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        OutlinedTextField(
                            value = message,
                            onValueChange = onMessageChanged,
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("输入消息…") },
                            shape = MaterialTheme.shapes.extraLarge,
                            maxLines = 4,
                            trailingIcon = {
                                IconButton(onClick = onSend) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.Send,
                                        contentDescription = "发送",
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            },
                        )
                    }
                }
            }
        },
    ) { padding ->
        Column(Modifier
            .fillMaxSize()
            .padding(padding)
            .consumeWindowInsets(padding)) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                reverseLayout = true,
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(messages, key = { it.id }) { message ->
                    val isMine = currentUserId != null && message.member.userId == currentUserId
                    MessageItem(
                        modifier = Modifier.animateItem(placementSpec = null),
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
}

@Preview
@Composable
private fun RoomPreview() {
    val alice = RoomMember(userId = 1, nickname = "小明")
    val bob = RoomMember(userId = 2, nickname = "小红")
    val messages = listOf(
        Message(1, alice, "你好，欢迎来到 Kio Chat！", alice.nickname),
        Message(2, bob, "小明，你好 👋", bob.nickname),
        Message(3, alice, "这是一条比较长的消息，用来预览正文自动换行的效果。", alice.nickname),
        Message(4, bob, "也可以发送多行文字。\n这是第二行。", bob.nickname),
    )
    KioChatTheme(dynamicColor = false) {
        RoomContent(room = Room(1, "Title"), messages = messages, message = "正在输入消息…", currentUserId = alice.userId,
            voiceParticipants = listOf(RoomMember(1, "小红")))
    }
}

private fun Context.getMissingVoiceCallPermissions(): List<String> = buildList {
    if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
        add(Manifest.permission.RECORD_AUDIO)
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
        checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
    ) {
        add(Manifest.permission.BLUETOOTH_CONNECT)
    }
}
