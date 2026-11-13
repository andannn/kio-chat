package me.andannn.kio.chat

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import me.andannn.kio.chat.ui.theme.KioChatTheme
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import kotlinx.coroutines.launch
import me.andannn.kio.chat.util.LocalSnackBarController
import me.andannn.kio.chat.util.Presenter
import me.andannn.kio.chat.util.RetainedPresenter
import me.andannn.kio.chat.util.SnackBarController
import me.andannn.kio.chat.util.rememberAndSetupSnackBarHostState
import me.andannn.kio.chat.util.retainPresenter
import org.koin.mp.KoinPlatform.getKoin

fun EntryProviderScope<NavKey>.loginEntry(navigator: Navigator) {
    entry<Screen.Login> {
        Login(navigator)
    }
}

@Composable
private fun retainAppState(
    navigator: Navigator,
    repository: ChatService = getKoin().get(),
    snackBarController: SnackBarController = LocalSnackBarController.current
) = retainPresenter(
    navigator,
    repository,
    snackBarController
) {
    LoginLayoutPresenter(navigator,repository, snackBarController)
}

sealed interface LoginUiEvent {
    data class OnUserNameChanged(val value: String) : LoginUiEvent
    data class OnUserPasswordChanged(val value: String) : LoginUiEvent
    data object OnLoginClick : LoginUiEvent
}

@Stable
private data class LoginLayoutState(
    val username: String,
    val password: String,
    val eventSink: (LoginUiEvent) -> Unit
)

private class LoginLayoutPresenter(
    val navigator: Navigator,
    val service: ChatService,
    val snackBarController: SnackBarController
) :
    RetainedPresenter<LoginLayoutState>() {
    @Composable
    override fun present(): LoginLayoutState {
        var username by rememberSaveable { mutableStateOf("") }
        var password by rememberSaveable { mutableStateOf("") }

        return LoginLayoutState(username, password) { event ->
            when (event) {
                is LoginUiEvent.OnUserNameChanged -> {
                    username = event.value
                }

                is LoginUiEvent.OnUserPasswordChanged -> {
                    password = event.value
                }

                LoginUiEvent.OnLoginClick -> {
                    retainedScope.launch {
                        val (_, error) = service.login(username, password)
                        if (error is ApiError.ServerError) {
                            snackBarController.showSnackBar(error.message)
                            return@launch
                        }

                        navigator.navigateTo(Screen.Home)
                    }
                }
            }
        }
    }
}

@Composable
private fun Login(
    navigator: Navigator,
    modifier: Modifier = Modifier,
    presenter: Presenter<LoginLayoutState> = retainAppState(navigator),
) {
    val state = presenter.present()
    LoginContent(
        username = state.username,
        password = state.password,
        modifier = modifier,
        onUserNameChanged = {
            state.eventSink.invoke(LoginUiEvent.OnUserNameChanged(it))
        },
        onPasswordChanged = {
            state.eventSink.invoke(LoginUiEvent.OnUserPasswordChanged(it))
        },
        onLogin = {
            state.eventSink.invoke(LoginUiEvent.OnLoginClick)
        }
    )
}

@Composable
private fun LoginContent(
    username: String,
    password: String,
    modifier: Modifier = Modifier,
    onUserNameChanged: (String) -> Unit = {},
    onPasswordChanged: (String) -> Unit = {},
    onLogin: () -> Unit = {},
) {

    Scaffold(
        modifier = modifier,
        snackbarHost = {
            SnackbarHost(
                modifier = Modifier.padding(bottom = 64.dp),
                hostState = rememberAndSetupSnackBarHostState(),
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 400.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                ) {
                    Column(
                        modifier = Modifier.padding(28.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                "Kio Chat",
                                style = MaterialTheme.typography.headlineLarge,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                "Sign in to continue",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        OutlinedTextField(
                            value = username,
                            onValueChange = { onUserNameChanged(it) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Username") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        )
                        OutlinedTextField(
                            value = password,
                            onValueChange = { onPasswordChanged(it) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Password") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Done,
                            ),
                        )
                        Button(onClick = onLogin, modifier = Modifier.fillMaxWidth()) {
                            Text("Sign in")
                        }
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "Don't have an account?",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = {}) { Text("Create account") }
                }
            }
        }
    }
}

@Preview
@Composable
private fun LoginPreview() {
    KioChatTheme(dynamicColor = false) {
        LoginContent("UserName", "Password")
    }
}