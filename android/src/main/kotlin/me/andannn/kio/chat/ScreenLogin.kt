package me.andannn.kio.chat

import android.util.Log
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import me.andannn.kio.chat.util.Presenter
import me.andannn.kio.chat.util.RetainedPresenter
import me.andannn.kio.chat.util.retainPresenter
import org.koin.mp.KoinPlatform.getKoin

fun EntryProviderScope<NavKey>.loginEntry(navigator: Navigator) {
    entry<Screen.Login> {
        Login(navigator)
    }
}

private const val TAG = "ScreenLogin"

@Composable
private fun retainAppState(
    repository: ChatService = getKoin().get(),
) = retainPresenter(
    repository
) {
    LoginLayoutPresenter(repository)
}

private class LoginLayoutState
private class LoginLayoutPresenter(val repository: ChatService) : RetainedPresenter<LoginLayoutState>() {
    @Composable
    override fun present(): LoginLayoutState {
        LaunchedEffect(Unit) {
            val a = repository.smock()
            Log.d(TAG, "present: $a")
        }
        return LoginLayoutState()
    }
}

@Composable
private fun Login(
    navigator: Navigator,
    modifier: Modifier = Modifier,
    presenter: Presenter<LoginLayoutState> = retainAppState(),
) {
    val state = presenter.present()
    LoginContent(state, modifier)
}

@Composable
private fun LoginContent(state: LoginLayoutState, modifier: Modifier = Modifier) {
    Scaffold(modifier = modifier) {
        LazyColumn(modifier = Modifier.padding(it)) {
            item {
                Text("Hello world")
            }
        }
    }
}

@Preview
@Composable
private fun LoginPreview() {
    LoginContent(
        LoginLayoutState()
    )
}