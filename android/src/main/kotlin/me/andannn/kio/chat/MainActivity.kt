package me.andannn.kio.chat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.window.DialogProperties
import kio.chat.domain.AppVersion
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.andannn.kio.chat.ui.theme.KioChatTheme
import org.koin.android.ext.android.getKoin
import kotlin.time.Duration.Companion.seconds

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        enableEdgeToEdge()

        super.onCreate(savedInstanceState)

        var initialScreen by mutableStateOf<Screen?>(null)
        var requiredVersion by mutableStateOf<AppVersion?>(null)

        lifecycleScope.launch {
            val service = getKoin().get<ChatService>()
            while (true) {
                val (version, error) = service.getAppVersion()
                if (error != null || version == null) {
                    delay(10.seconds)
                    continue
                }
                if (BuildConfig.VERSION_CODE < version.versionCode) {
                    requiredVersion = version
                    return@launch
                }
                break
            }

            while (true) {
                val (_, error) = service.checkLogin()
                if (error == null) {
                    initialScreen = Screen.Home
                    break
                } else if (error == ApiError.AuthenticationError) {
                    initialScreen = Screen.Login
                    break
                } else {
                    delay(10.seconds)
                }
            }
        }

        splashScreen.setKeepOnScreenCondition {
            initialScreen == null && requiredVersion == null
        }

        setContent {
            KioChatTheme {
                initialScreen?.let { ChatApp(it) }

                requiredVersion?.let { version ->
                    val uriHandler = LocalUriHandler.current
                    AlertDialog(
                        onDismissRequest = {},
                        properties = DialogProperties(
                            dismissOnBackPress = false,
                            dismissOnClickOutside = false,
                        ),
                        title = { Text("请更新后继续使用") },
                        text = {
                            Text("当前版本：${BuildConfig.VERSION_NAME}\n最新版本：${version.versionName}")
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                uriHandler.openUri("https://133.117.75.202:7879/downloads/kio-chat.apk")
                            }) { Text("下载更新") }
                        },
                    )
                }
            }
        }
    }
}
