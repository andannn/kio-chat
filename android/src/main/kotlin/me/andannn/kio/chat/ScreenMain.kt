package me.andannn.kio.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.retain.retain
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.scene.DialogSceneStrategy
import androidx.navigation3.scene.SinglePaneSceneStrategy
import androidx.navigation3.ui.NavDisplay
import kio.chat.domain.Room
import kio.chat.domain.RoomMember
import kio.chat.domain.WebSocketSessionReady
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.seconds
import me.andannn.kio.chat.util.Presenter
import me.andannn.kio.chat.util.RetainedPresenter
import me.andannn.kio.chat.util.rememberRetainedValueStoreNavEntryDecorator
import me.andannn.kio.chat.util.rememberSnackBarControllerNavEntryDecorator
import me.andannn.kio.chat.util.retainPresenter
import me.andannn.kio.chat.util.stateInRetainedModel
import me.andannn.kio.chat.widget.VoiceCallBubble
import org.koin.mp.KoinPlatform.getKoin

fun EntryProviderScope<NavKey>.mainEntry(rootNavigator: RootNavigator) {
    entry<Screen.Main> {
        MainScreen(rootNavigator)
    }
}

@Composable
private fun retainMainState(
    rootNavigator: RootNavigator,
    voiceRepository: VoiceCallRepository = getKoin().get(),
    eventSource: UserEventSource = getKoin().get(),
    userRepository: UserRepository = getKoin().get(),
) = retainPresenter(rootNavigator, voiceRepository, eventSource, userRepository) {
    MainLayoutPresenter(rootNavigator, voiceRepository, eventSource, userRepository)
}

private data class MainLayoutState(
    val connected: Boolean,
    val callState: VoiceCallState,
    val activeRoom: Room?,
    val participants: List<RoomMember>,
    val isBusy: Boolean,
    val onToggleMuted: () -> Unit,
    val onLeaveVoice: () -> Unit,
)

private class MainLayoutPresenter(
    private val rootNavigator: RootNavigator,
    private val voiceRepository: VoiceCallRepository,
    eventSource: UserEventSource,
    private val userRepository: UserRepository,
) : RetainedPresenter<MainLayoutState>() {
    private var connected by mutableStateOf(true)
    private var isBusy by mutableStateOf(false)

    private val roomsFlow = userRepository.observeRooms {}.stateInRetainedModel(emptyList())

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val participantsFlow = voiceRepository.callState
        .flatMapLatest { state ->
            val roomId = state.roomId
            if (roomId != null) {
                userRepository.observeVoiceParticipants(roomId) {}
            } else {
                flowOf(emptyList())
            }
        }
        .stateInRetainedModel(emptyList())

    init {
        // Subscribe before opening the socket so initial events have consumers.
        val voiceJob = with(voiceRepository) { retainedScope.startObservingEvents() }
        retainedScope.launch {
            eventSource.events
                .filterIsInstance<WebSocketSessionReady>()
                .collect { connected = true }
        }
        retainedScope.launch {
            try {
                while (isActive) {
                    val error = eventSource.observe()
                    connected = false
                    if (error == ApiError.AuthenticationError) {
                        rootNavigator.clearTopAndGoToLogin()
                        break
                    }
                    delay(3.seconds)
                }
            } finally {
                withContext(NonCancellable) {
                    voiceJob.cancelAndJoin()
                }
            }
        }
    }

    private fun changeVoice(action: suspend () -> Unit) {
        if (isBusy) return
        isBusy = true
        retainedScope.launch {
            try {
                action()
            } finally {
                isBusy = false
            }
        }
    }

    @Composable
    override fun present(): MainLayoutState {
        val callState by voiceRepository.callState.collectAsStateWithLifecycle()
        val rooms by roomsFlow.collectAsStateWithLifecycle()
        val participants by participantsFlow.collectAsStateWithLifecycle()

        val activeRoom = rooms.firstOrNull { it.id == callState.roomId }

        return MainLayoutState(
            connected = connected,
            callState = callState,
            activeRoom = activeRoom,
            participants = participants,
            isBusy = isBusy,
            onToggleMuted = {
                changeVoice {
                    voiceRepository.setMuted(!callState.muted)
                }
            },
            onLeaveVoice = {
                changeVoice {
                    voiceRepository.leave()
                }
            },
        )
    }
}

@Composable
private fun MainScreen(
    rootNavigator: RootNavigator,
    modifier: Modifier = Modifier,
    presenter: Presenter<MainLayoutState> = retainMainState(rootNavigator),
) {
    val state = presenter.present()
    Box(modifier) {
        MainContent(rootNavigator, Modifier.fillMaxSize(), state)
    }
}

@Composable
private fun MainContent(rootNavigator: RootNavigator, modifier: Modifier = Modifier, state: MainLayoutState) {
    val layoutDirection = LocalLayoutDirection.current
    val backStack = rememberNavBackStack(MainScreen.Home)
    val navigator = retain { MainNavigatorImpl() }
    DisposableEffect(backStack) {
        navigator.backStack = backStack
        onDispose { navigator.backStack = null }
    }

    val currentTopScreen = backStack.lastOrNull()
    val isInsideCurrentCallRoom = currentTopScreen is MainScreen.RoomChat
    val showGlobalBubble = state.callState.roomId != null && !isInsideCurrentCallRoom

    Box(modifier) {
        Column(Modifier.fillMaxSize()) {
            if (!state.connected) {
                Surface(
                    color = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { liveRegion = LiveRegionMode.Assertive },
                ) {
                    Row(
                        modifier = Modifier
                            .statusBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.CloudOff, contentDescription = null)
                        Column {
                            Text("连接已断开，正在重连...", style = MaterialTheme.typography.titleSmall)
                            Text("消息和通话暂不可用", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            NavDisplay(
                modifier = Modifier
                    .weight(1f)
                    .then(
                        if (!state.connected) Modifier.consumeWindowInsets(WindowInsets.statusBars) else Modifier,
                    ),
                backStack = backStack,
                transitionSpec = { horizontalSharedAxis(forward = true, layoutDirection) },
                popTransitionSpec = { horizontalSharedAxis(forward = false, layoutDirection) },
                predictivePopTransitionSpec = { horizontalSharedAxis(forward = false, layoutDirection) },
                sceneStrategies = listOf(DialogSceneStrategy(), SinglePaneSceneStrategy()),
                entryDecorators = listOf(
                    rememberSaveableStateHolderNavEntryDecorator(),
                    rememberRetainedValueStoreNavEntryDecorator(),
                    rememberSnackBarControllerNavEntryDecorator(),
                ),
                entryProvider = entryProvider {
                    homeEntry(rootNavigator, navigator)
                    roomJoinRequestsEntry(rootNavigator, navigator)
                    roomEntry(rootNavigator, navigator)
                    roomSettingEntry(rootNavigator, navigator)
                },
            )
        }

        // App 内全局通话悬浮气泡：当在通话中且不在该 Room 页面（例如在 Home 或其他房间）时浮现
        AnimatedVisibility(
            visible = showGlobalBubble,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 16.dp, vertical = 20.dp),
        ) {
            val roomId = state.callState.roomId ?: return@AnimatedVisibility
            VoiceCallBubble(
                roomId = roomId,
                participants = state.participants,
                call = state.callState,
                isBusy = state.isBusy,
                onToggleMuted = state.onToggleMuted,
                onJoinVoice = {},
                onLeaveVoice = state.onLeaveVoice,
                roomName = state.activeRoom?.name,
                onClick = {
                    // 点击全局气泡一键跳回正在通话的 Room
                    val targetRoom = state.activeRoom ?: Room(roomId, "房间 $roomId")
                    navigator.navigateTo(MainScreen.RoomChat(targetRoom))
                },
            )
        }
    }
}
