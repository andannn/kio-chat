package me.andannn.kio.chat

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.retain.retain
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.scene.DialogSceneStrategy
import androidx.navigation3.scene.SinglePaneSceneStrategy
import androidx.navigation3.ui.NavDisplay
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.andannn.kio.chat.util.Presenter
import me.andannn.kio.chat.util.RetainedPresenter
import me.andannn.kio.chat.util.rememberRetainedValueStoreNavEntryDecorator
import me.andannn.kio.chat.util.rememberSnackBarControllerNavEntryDecorator
import me.andannn.kio.chat.util.retainPresenter
import org.koin.mp.KoinPlatform.getKoin

fun EntryProviderScope<NavKey>.mainEntry(rootNavigator: RootNavigator) {
    entry<Screen.Main> {
        MainScreen(rootNavigator)
    }
}

@Composable
private fun retainMainState(
    voiceRepository: VoiceCallRepository = getKoin().get(),
    eventSource: UserEventSource = getKoin().get(),
) = retainPresenter(voiceRepository, eventSource) {
    MainLayoutPresenter( voiceRepository, eventSource)
}

private data class MainLayoutState(val connected: Boolean)

private class MainLayoutPresenter(
    voiceRepository: VoiceCallRepository,
    eventSource: UserEventSource,
) : RetainedPresenter<MainLayoutState>() {
    private var connected by mutableStateOf(true)

    init {
        // Subscribe before opening the socket so initial events have consumers.
        val voiceJob = with(voiceRepository) { retainedScope.startObservingEvents() }
        retainedScope.launch {
            try {
                eventSource.observe()
            } finally {
                withContext(NonCancellable) {
                    voiceJob.cancelAndJoin()
                }
            }
            connected = false
        }
    }

    @Composable
    override fun present() = MainLayoutState(connected = connected)
}

@Composable
private fun MainScreen(
    rootNavigator: RootNavigator,
    modifier: Modifier = Modifier,
    presenter: Presenter<MainLayoutState> = retainMainState(),
) {
    val state = presenter.present()
    Box(modifier) {
        MainContent(rootNavigator, Modifier.fillMaxSize(), state.connected)
    }
}

@Composable
private fun MainContent(rootNavigator: RootNavigator, modifier: Modifier = Modifier, connected: Boolean) {
    val layoutDirection = LocalLayoutDirection.current
    val backStack = rememberNavBackStack(MainScreen.Home)
    val navigator = retain { MainNavigatorImpl() }
    DisposableEffect(backStack) {
        navigator.backStack = backStack
        onDispose { navigator.backStack = null }
    }

    Column(modifier) {
        if (!connected) {
            Surface(
                color = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
                modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Assertive },
            ) {
                Row(
                    modifier = Modifier.statusBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.CloudOff, contentDescription = null)
                    Column {
                        Text("连接已断开", style = MaterialTheme.typography.titleSmall)
                        Text("消息和通话暂不可用", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        NavDisplay(
            modifier = Modifier.weight(1f).then(
                if (!connected) Modifier.consumeWindowInsets(WindowInsets.statusBars) else Modifier,
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
}
