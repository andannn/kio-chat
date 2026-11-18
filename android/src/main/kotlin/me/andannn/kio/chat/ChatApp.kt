package me.andannn.kio.chat

import android.util.Log
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.retain.retain
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
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
    val layoutDirection = LocalLayoutDirection.current

    val backStack = rememberNavBackStack(initialScreen)
    val navigator = retain { RootNavigatorImpl() }
    DisposableEffect(backStack) {
        navigator.backStack = backStack

        onDispose {
            navigator.backStack = null
        }
    }

    NavDisplay(
        modifier = modifier,
        backStack = backStack,
        transitionSpec = { horizontalSharedAxis(forward = true, layoutDirection) },
        popTransitionSpec = { horizontalSharedAxis(forward = false, layoutDirection) },
        predictivePopTransitionSpec = { horizontalSharedAxis(forward = false, layoutDirection) },
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
                mainEntry(navigator)
            }
    )
}

fun horizontalSharedAxis(forward: Boolean, layoutDirection: LayoutDirection): ContentTransform {
    val direction = (if (forward) 1 else -1) * (if (layoutDirection == LayoutDirection.Ltr) 1 else -1)
    val fadeSpec = tween<Float>(durationMillis = 90, delayMillis = 50, easing = LinearEasing)
    val transform = (slideInHorizontally { it / 5 * direction } + fadeIn(fadeSpec)) togetherWith
        (slideOutHorizontally { -it / 5 * direction } + fadeOut(fadeSpec))
    transform.targetContentZIndex = if (forward) 1f else -1f
    return transform
}
