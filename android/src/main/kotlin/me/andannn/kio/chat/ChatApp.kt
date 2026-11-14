package me.andannn.kio.chat

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.retain.retain
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.scene.DialogSceneStrategy
import androidx.navigation3.scene.SinglePaneSceneStrategy
import androidx.navigation3.ui.NavDisplay
import me.andannn.kio.chat.util.rememberRetainedValueStoreNavEntryDecorator
import me.andannn.kio.chat.util.rememberSnackBarControllerNavEntryDecorator

private const val TAG = "App"

@Composable
fun ChatApp(
    initialScreen: Screen,
    modifier: Modifier = Modifier,
) {
    Log.d(TAG, "ChatApp: initialScreen = $initialScreen")
    val backStack = rememberNavBackStack(initialScreen)
    val navigator = retain { RootNavigator() }

    DisposableEffect(backStack) {
        navigator.backStack = backStack

        onDispose {
            navigator.backStack = null
        }
    }

    NavDisplay(
        modifier = modifier,
        backStack = backStack,
        sceneStrategies =
            listOf(
                DialogSceneStrategy(),
                SinglePaneSceneStrategy(),
            ),
        entryDecorators =
            listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberRetainedValueStoreNavEntryDecorator(),
                rememberSnackBarControllerNavEntryDecorator(),
            ),
        entryProvider =
            entryProvider {
                loginEntry(navigator)
                registerEntry(navigator)
                roomsEntry(navigator)
                roomEntry(navigator)
                roomMembersEntry(navigator)
            },
    )
}
