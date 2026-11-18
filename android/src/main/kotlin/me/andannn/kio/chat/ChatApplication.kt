package me.andannn.kio.chat

import android.app.Application
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

class ChatApplication: Application() {
    override fun onCreate() {
        super.onCreate()

        startKoin {
            androidContext(this@ChatApplication)
            modules(
                module {
                    singleOf(::ChatServiceImpl).bind(ChatService::class)
                    singleOf(::UserEventSource)
                    single<VoiceCallRepository> { VoiceCallRepositoryImpl(androidContext(), get(), get()) }
                    singleOf(::UserRepositoryImpl).bind(UserRepository::class)
                },
            )
        }
    }
}