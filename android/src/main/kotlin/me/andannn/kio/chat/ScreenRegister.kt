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
import androidx.compose.runtime.remember
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

fun EntryProviderScope<NavKey>.registerEntry(navigator: Navigator) {
    entry<Screen.Register> {
        Register(navigator)
    }
}

@Composable
private fun retainRegisterState(
    navigator: Navigator,
    repository: ChatService = getKoin().get(),
    snackBarController: SnackBarController = LocalSnackBarController.current
) = retainPresenter(
    navigator,
    repository,
    snackBarController
) {
    RegisterLayoutPresenter(navigator, repository, snackBarController)
}

sealed interface RegisterUiEvent {
    data class OnUserNameChanged(val value: String) : RegisterUiEvent
    data class OnUserPasswordChanged(val value: String) : RegisterUiEvent
    data class OnConfirmPasswordChanged(val value: String) : RegisterUiEvent
    data object OnBackClick : RegisterUiEvent
    data object OnRegisterClick : RegisterUiEvent
}

@Stable
private data class RegisterLayoutState(
    val username: String,
    val password: String,
    val confirmPassword: String,
    val isSubmitting: Boolean,
    val eventSink: (RegisterUiEvent) -> Unit
)

private class RegisterLayoutPresenter(
    val navigator: Navigator,
    val service: ChatService,
    val snackBarController: SnackBarController
) :
    RetainedPresenter<RegisterLayoutState>() {
    @Composable
    override fun present(): RegisterLayoutState {
        var username by rememberSaveable { mutableStateOf("") }
        var password by rememberSaveable { mutableStateOf("") }

        var confirmPassword by rememberSaveable { mutableStateOf("") }
        var isSubmitting by remember { mutableStateOf(false) }

        context(navigator, snackBarController) {
            return RegisterLayoutState(username, password, confirmPassword, isSubmitting) { event ->
                when (event) {
                    is RegisterUiEvent.OnUserNameChanged -> {
                        username = event.value
                    }

                    is RegisterUiEvent.OnUserPasswordChanged -> {
                        password = event.value
                    }

                    is RegisterUiEvent.OnConfirmPasswordChanged -> confirmPassword = event.value
                    RegisterUiEvent.OnBackClick -> navigator.popBackStack()
                    RegisterUiEvent.OnRegisterClick -> {
                        if (!isSubmitting) {
                            val name = username
                            val secret = password
                            val confirmation = confirmPassword
                            retainedScope.launch {
                                isSubmitting = true
                                val (_, error) = service.register(name, secret, confirmation)
                                isSubmitting = false
                                if (error != null) {
                                    handleError(error = error)
                                    return@launch
                                }

                                navigator.clearTopAndGoToRooms()
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Register(
    navigator: Navigator,
    modifier: Modifier = Modifier,
    presenter: Presenter<RegisterLayoutState> = retainRegisterState(navigator),
) {
    val state = presenter.present()
    RegisterContent(
        username = state.username,
        password = state.password,
        confirmPassword = state.confirmPassword,
        isSubmitting = state.isSubmitting,
        modifier = modifier,
        snackbarHostState = rememberAndSetupSnackBarHostState(),
        onUserNameChanged = {
            state.eventSink.invoke(RegisterUiEvent.OnUserNameChanged(it))
        },
        onPasswordChanged = {
            state.eventSink.invoke(RegisterUiEvent.OnUserPasswordChanged(it))
        },
        onRegister = {
            state.eventSink.invoke(RegisterUiEvent.OnRegisterClick)
        },
        onBack = { state.eventSink(RegisterUiEvent.OnBackClick) },
        onConfirmPasswordChanged = { state.eventSink(RegisterUiEvent.OnConfirmPasswordChanged(it)) },
    )
}

@Composable
private fun RegisterContent(
    username: String,
    password: String,
    confirmPassword: String,
    modifier: Modifier = Modifier,
    isSubmitting: Boolean = false,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    onUserNameChanged: (String) -> Unit = {},
    onPasswordChanged: (String) -> Unit = {},
    onBack: () -> Unit = {},
    onConfirmPasswordChanged: (String) -> Unit = {},
    onRegister: () -> Unit = {},
) {
    Scaffold(
        modifier = modifier.imePadding(),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
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
                                "创建账号",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        OutlinedTextField(
                            value = username,
                            onValueChange = { onUserNameChanged(it) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("用户名") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        )
                        OutlinedTextField(
                            value = password,
                            onValueChange = { onPasswordChanged(it) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("密码") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Done,
                            ),
                        )
                        OutlinedTextField(
                            value = confirmPassword,
                            onValueChange = onConfirmPasswordChanged,
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("确认密码") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Done,
                            ),
                        )
                        Button(onClick = onRegister, enabled = !isSubmitting, modifier = Modifier.fillMaxWidth()) {
                            Text(if (isSubmitting) "注册中…" else "注册")
                        }
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "已有账号？",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = onBack) { Text("返回登录") }
                }
            }
        }
    }
}

@Preview
@Composable
private fun RegisterPreview() {
    KioChatTheme(dynamicColor = false) {
        RegisterContent("用户名", "密码", "密码")
    }
}