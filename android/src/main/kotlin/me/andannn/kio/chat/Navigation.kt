package me.andannn.kio.chat

import androidx.compose.runtime.retain.RetainObserver
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey


/**
 * Navigator interface for navigation between screens.
 */
interface Navigator {
    /**
     * Navigate to a new screen.
     *
     * @param screen The screen to navigate to.
     */
    fun navigateTo(screen: Screen)

    fun popToLogin()

    /**
     * Pop the current screen from the back stack.
     */
    fun popBackStack()
}

class RootNavigator :
    RetainObserver,
    Navigator {
    var backStack: NavBackStack<NavKey>? = null

    override fun navigateTo(screen: Screen) {
        backStack?.add(screen) ?: error("backStack is null")
    }

    override fun popToLogin() {
        backStack?.clear()
        backStack?.add(Screen.Login)
    }

    override fun popBackStack() {
        with(backStack ?: error("backStack is null")) {
            if (size > 1) {
                removeAt(lastIndex)
            }
        }
    }

    override fun onRetained() {}

    override fun onEnteredComposition() {}

    override fun onExitedComposition() {}

    override fun onRetired() {
        backStack = null
    }

    override fun onUnused() {
        backStack = null
    }
}
