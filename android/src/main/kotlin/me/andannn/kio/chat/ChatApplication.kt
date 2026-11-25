package me.andannn.kio.chat

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.util.DebugLogger
import io.ktor.client.HttpClient
import org.koin.android.ext.koin.androidContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.context.startKoin
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module
import timber.log.Timber

class ChatApplication :
    Application(),
    SingletonImageLoader.Factory,
    KoinComponent {
    override fun onCreate() {
        super.onCreate()

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        startKoin {
            androidContext(this@ChatApplication)
            modules(
                module {
                    single { createHttpClient() }
                    singleOf(::ChatServiceImpl).bind(ChatService::class)
                    singleOf(::UserRepositoryImpl).bind(UserRepository::class)
                    singleOf(::VoiceCallRepositoryImpl).bind(VoiceCallRepository::class)
                    singleOf(::UserEventSource)
                },
            )
        }
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .apply {
                if (BuildConfig.DEBUG) {
                    logger(DebugLogger())
                }
            }
            .components {
                add(
                    KtorNetworkFetcherFactory(
                        httpClient = { get<HttpClient>() },
                    ),
                )
            }
            .build()
}