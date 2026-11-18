package me.andannn.kio.chat

import androidx.navigation3.runtime.NavKey
import kio.chat.domain.Room
import kotlinx.serialization.Serializable

@Serializable
sealed interface Screen : NavKey {
    @Serializable
    data object Login : Screen
    @Serializable
    data object Register : Screen
    @Serializable
    data object Main: Screen
}

@Serializable
sealed interface MainScreen: NavKey {
    @Serializable
    data object Home : MainScreen
    @Serializable
    data object RoomJoinRequests : MainScreen
    @Serializable
    data class RoomChat(val room: Room) : MainScreen
    @Serializable
    data class RoomSetting(val room: Room) : MainScreen
}