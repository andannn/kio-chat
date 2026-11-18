package me.andannn.kio.chat

import androidx.compose.runtime.retain.RetainObserver
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey

interface RootNavigator {
    fun navigateTo(screen: Screen)
    fun clearTopAndGoToLogin()
    fun clearTopAndGoToMain()
    fun popBackStack()
}

class RootNavigatorImpl :
    RetainObserver,
    RootNavigator {
    var backStack: NavBackStack<NavKey>? = null

    override fun navigateTo(screen: Screen) {
        backStack?.add(screen) ?: error("backStack is null")
    }

    override fun clearTopAndGoToLogin() {
        backStack?.clear()
        backStack?.add(Screen.Login)
    }

    override fun clearTopAndGoToMain() {
        backStack?.clear()
        backStack?.add(Screen.Main)
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


interface MainNavigator {
    fun navigateTo(screen: MainScreen)
    fun clearTopAndGoToHome()
    fun popBackStack()
}

class MainNavigatorImpl :
    RetainObserver,
    MainNavigator {
    var backStack: NavBackStack<NavKey>? = null

    override fun navigateTo(screen: MainScreen) {
        backStack?.add(screen) ?: error("backStack is null")
    }

    override fun clearTopAndGoToHome() {
        backStack?.clear()
        backStack?.add(MainScreen.Home)
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
