package me.andannn.kio.chat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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

        lifecycleScope.launch {
            while (true) {
                val (_, error) = getKoin().get<ChatService>().checkLogin()
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
            initialScreen == null
        }

        setContent {
            KioChatTheme {
                initialScreen?.let { ChatApp(it) }
            }
        }
    }
}
