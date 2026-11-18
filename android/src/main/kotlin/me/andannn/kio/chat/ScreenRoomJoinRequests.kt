package me.andannn.kio.chat

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import kio.chat.domain.RoomJoinRequest
import kotlinx.coroutines.launch
import me.andannn.kio.chat.ui.theme.KioChatTheme
import me.andannn.kio.chat.util.LocalSnackBarController
import me.andannn.kio.chat.util.Presenter
import me.andannn.kio.chat.util.RetainedPresenter
import me.andannn.kio.chat.util.SnackBarController
import me.andannn.kio.chat.util.rememberAndSetupSnackBarHostState
import me.andannn.kio.chat.util.retainPresenter
import me.andannn.kio.chat.util.stateInRetainedModel
import org.koin.mp.KoinPlatform.getKoin

fun EntryProviderScope<NavKey>.roomJoinRequestsEntry(rootNavigator: RootNavigator, navigator: MainNavigator) {
    entry<MainScreen.RoomJoinRequests> { RoomJoinRequests(rootNavigator, navigator) }
}

@Composable
private fun retainRoomJoinRequestsState(
    rootNavigator: RootNavigator,
    navigator: MainNavigator,
    repository: UserRepository = LocalUserRepository.current,
    service: ChatService = getKoin().get(),
    snackBarController: SnackBarController = LocalSnackBarController.current,
) = retainPresenter(rootNavigator, navigator, repository, service, snackBarController) {
    RoomJoinRequestsLayoutPresenter(rootNavigator, navigator, repository, service, snackBarController)
}

sealed interface RoomJoinRequestsUiEvent {
    data object OnBackClick : RoomJoinRequestsUiEvent
    data class OnApprove(val requestId: Long) : RoomJoinRequestsUiEvent
    data class OnReject(val requestId: Long) : RoomJoinRequestsUiEvent
}

private data class RoomJoinRequestsLayoutState(
    val requests: List<RoomJoinRequest>,
    val eventSink: (RoomJoinRequestsUiEvent) -> Unit,
)

private class RoomJoinRequestsLayoutPresenter(
    private val rootNavigator: RootNavigator,
    private val navigator: MainNavigator,
    repository: UserRepository,
    private val service: ChatService,
    private val snackBarController: SnackBarController,
) : RetainedPresenter<RoomJoinRequestsLayoutState>() {
    private val requests = repository.observeRoomJoinRequest().stateInRetainedModel(emptyList())

    private fun reviewRequest(requestId: Long, approve: Boolean) {
        retainedScope.launch {
            val (_, error) = if (approve) service.approveRoomJoinRequest(requestId)
                else service.rejectRoomJoinRequest(requestId)
            context(rootNavigator, snackBarController) {
                if (error != null) handleError(error)
            }
        }
    }

    @Composable
    override fun present(): RoomJoinRequestsLayoutState {
        val currentRequests by requests.collectAsStateWithLifecycle()
        return RoomJoinRequestsLayoutState(currentRequests) { event ->
            when (event) {
                RoomJoinRequestsUiEvent.OnBackClick -> navigator.popBackStack()
                is RoomJoinRequestsUiEvent.OnApprove -> reviewRequest(event.requestId, approve = true)
                is RoomJoinRequestsUiEvent.OnReject -> reviewRequest(event.requestId, approve = false)
            }
        }
    }
}

@Composable
private fun RoomJoinRequests(
    rootNavigator: RootNavigator,
    navigator: MainNavigator,
    modifier: Modifier = Modifier,
    presenter: Presenter<RoomJoinRequestsLayoutState> = retainRoomJoinRequestsState(rootNavigator, navigator),
) {
    val state = presenter.present()
    RoomJoinRequestsContent(
        requests = state.requests,
        modifier = modifier,
        snackbarHostState = rememberAndSetupSnackBarHostState(),
        onBack = { state.eventSink(RoomJoinRequestsUiEvent.OnBackClick) },
        onApprove = { state.eventSink(RoomJoinRequestsUiEvent.OnApprove(it)) },
        onReject = { state.eventSink(RoomJoinRequestsUiEvent.OnReject(it)) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RoomJoinRequestsContent(
    requests: List<RoomJoinRequest>,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    onBack: () -> Unit = {},
    onApprove: (Long) -> Unit = {},
    onReject: (Long) -> Unit = {},
) {
    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("入群申请（${requests.size}）") },
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
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (requests.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 64.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("还没有新的入群申请", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            items(requests, key = { it.id }) { request ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                ) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(request.nickname, style = MaterialTheme.typography.titleMedium)
                        Text(
                            "申请加入「${request.roomName}」",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = { onReject(request.id) }) { Text("拒绝") }
                            FilledTonalButton(onClick = { onApprove(request.id) }) { Text("同意") }
                        }
                    }
                }
            }
        }
    }
}

@Preview
@Composable
private fun RoomJoinRequestsPreview() {
    KioChatTheme(dynamicColor = false) {
        RoomJoinRequestsContent(listOf(RoomJoinRequest(1, 2, "日常交流", 3, "小明")))
    }
}
