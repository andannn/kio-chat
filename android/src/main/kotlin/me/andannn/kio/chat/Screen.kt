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
    data object Home : Screen
    @Serializable
    data object RoomJoinRequests : Screen
    @Serializable
    data class RoomChat(val room: Room) : Screen
    @Serializable
    data class RoomSetting(val room: Room) : Screen
}
