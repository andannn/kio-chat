package me.andannn.kio.chat

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import me.andannn.kio.chat.ui.theme.KioChatTheme
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import me.andannn.kio.chat.util.Presenter
import me.andannn.kio.chat.util.RetainedPresenter
import me.andannn.kio.chat.util.retainPresenter

fun EntryProviderScope<NavKey>.homeEntry(navigator: Navigator) {
    entry<Screen.Home> {
        Home(navigator)
    }
}

@Composable
private fun retainHomeState(navigator: Navigator) = retainPresenter(navigator) {
    HomeLayoutPresenter(navigator)
}

private class HomeLayoutState

private class HomeLayoutPresenter(val navigator: Navigator) : RetainedPresenter<HomeLayoutState>() {
    @Composable
    override fun present(): HomeLayoutState = HomeLayoutState()
}

@Composable
private fun Home(
    navigator: Navigator,
    modifier: Modifier = Modifier,
    presenter: Presenter<HomeLayoutState> = retainHomeState(navigator),
) {
    HomeContent(presenter.present(), modifier)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeContent(state: HomeLayoutState, modifier: Modifier = Modifier) {
    var message by rememberSaveable { mutableStateOf("") }

    Scaffold(
        modifier = modifier.imePadding(),
        topBar = {
            TopAppBar(
                title = { Text("Kio Chat") },
                actions = {
                    TextButton(onClick = {}) { Text("Logout") }
                },
            )
        },
        bottomBar = {
            BottomAppBar(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
                OutlinedTextField(
                    value = message,
                    onValueChange = { message = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Write a message…") },
                    shape = MaterialTheme.shapes.extraLarge,
                    maxLines = 4,
                )
                Spacer(Modifier.width(12.dp))
                Button(onClick = {}) { Text("Send") }
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Message items will be supplied by the screen state.
        }
    }
}

@Preview
@Composable
private fun HomePreview() {
    KioChatTheme(dynamicColor = false) {
        HomeContent(HomeLayoutState())
    }
}
