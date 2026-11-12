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
import me.andannn.kio.chat.util.Presenter
import me.andannn.kio.chat.util.RetainedPresenter
import me.andannn.kio.chat.util.retainPresenter
import org.koin.mp.KoinPlatform.getKoin

private const val TAG = "App"

@Composable
internal fun retainAppState(
    repository: ChatServiceImpl = getKoin().get(),
) = retainPresenter(
    repository
) {
    AppLayoutPresenter(repository)
}

class AppLayoutState
internal class AppLayoutPresenter(val repository: ChatService) : RetainedPresenter<AppLayoutState>() {
    @Composable
    override fun present(): AppLayoutState {
        LaunchedEffect(Unit) {
            val a = repository.smock()
            Log.d(TAG, "present: $a")
        }
        return AppLayoutState()
    }
}

@Composable
fun App(
    modifier: Modifier = Modifier,
    presenter: Presenter<AppLayoutState> = retainAppState(),
) {
    val state = presenter.present()
    AppContent(modifier = modifier, state = state)
}

@Composable
private fun AppContent(state: AppLayoutState, modifier: Modifier = Modifier) {
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
fun AppAndroidPreview() {
    AppContent(
        AppLayoutState()
    )
}