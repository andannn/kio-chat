package me.andannn.kio.chat

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.SnackbarHost
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.retain.retain
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
import me.andannn.kio.chat.util.LocalSnackBarController
import me.andannn.kio.chat.util.Presenter
import me.andannn.kio.chat.util.RetainedPresenter
import me.andannn.kio.chat.util.rememberAndSetupSnackBarHostState
import me.andannn.kio.chat.util.rememberRetainedValueStoreNavEntryDecorator
import me.andannn.kio.chat.util.rememberSnackBarControllerNavEntryDecorator
import me.andannn.kio.chat.util.retainPresenter
import org.koin.mp.KoinPlatform.getKoin

fun EntryProviderScope<NavKey>.mainEntry(rootNavigator: RootNavigator) {
    entry<Screen.Main> {
        val eventSource = getKoin().get<UserEventSource>()
        val service = getKoin().get<ChatService>()
        val context = LocalContext.current.applicationContext
        val userRepository: UserRepository = retain(eventSource) { UserRepositoryImpl(eventSource) }
        val voiceRepository: VoiceCallRepository = retain(context, service, eventSource) {
            VoiceCallRepositoryImpl(context, service, eventSource)
        }
        MainScreen(rootNavigator, userRepository, voiceRepository)
    }
}

@Composable
private fun retainMainState(
    userRepository: UserRepository,
    voiceRepository: VoiceCallRepository,
    eventSource: UserEventSource = getKoin().get(),
) = retainPresenter(userRepository, voiceRepository, eventSource) {
    MainLayoutPresenter(userRepository, voiceRepository, eventSource)
}

private data class MainLayoutState(val error: ApiError? = null)

private class MainLayoutPresenter(
    userRepository: UserRepository,
    voiceRepository: VoiceCallRepository,
    eventSource: UserEventSource,
) : RetainedPresenter<MainLayoutState>() {
    private var connectionError by mutableStateOf<ApiError?>(null)

    init {
        // Subscribe before opening the socket so initial events have consumers.
        val userJob = with(userRepository) { retainedScope.startObservingEvents() }
        val voiceJob = with(voiceRepository) { retainedScope.startObservingEvents() }
        retainedScope.launch {
            val error = try {
                eventSource.observe()
            } finally {
                withContext(NonCancellable) {
                    voiceJob.cancelAndJoin()
                    userJob.cancelAndJoin()
                }
            }
            connectionError = error ?: ApiError.ServerError("实时连接已断开，请重新登录。")
        }
    }

    @Composable
    override fun present() = MainLayoutState(connectionError)
}

@Composable
private fun MainScreen(
    rootNavigator: RootNavigator,
    userRepository: UserRepository,
    voiceRepository: VoiceCallRepository,
    modifier: Modifier = Modifier,
    presenter: Presenter<MainLayoutState> = retainMainState(userRepository, voiceRepository),
) {
    val snackBarController = LocalSnackBarController.current
    val snackbarHostState = rememberAndSetupSnackBarHostState(snackBarController)
    CompositionLocalProvider(
        LocalUserRepository provides userRepository,
        LocalVoiceCallRepository provides voiceRepository,
    ) {
        val state = presenter.present()
        LaunchedEffect(state.error) {
            context(rootNavigator, snackBarController) {
                state.error?.let { handleError(it) }
            }
        }
        Box(modifier) {
            MainContent(rootNavigator, Modifier.fillMaxSize())
            SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
        }
    }
}

@Composable
private fun MainContent(rootNavigator: RootNavigator, modifier: Modifier = Modifier) {
    val layoutDirection = LocalLayoutDirection.current
    val backStack = rememberNavBackStack(MainScreen.Home)
    val navigator = retain { MainNavigatorImpl() }
    DisposableEffect(backStack) {
        navigator.backStack = backStack
        onDispose { navigator.backStack = null }
    }

    NavDisplay(
        modifier = modifier,
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
