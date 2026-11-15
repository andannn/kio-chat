package kio.chat.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("user")
data class User(
    @SerialName("id") val id: Long,
    @SerialName("username") val username: String,
    @SerialName("passwordHash") val passwordHash: String,
)

@Serializable
@SerialName("session")
data class Session(
    @SerialName("userId") val userId: Long,
)

@Serializable
@SerialName("message")
data class Message(
    @SerialName("id") val id: Long,
    @SerialName("user") val user: User,
    @SerialName("text") val text: String,
    @SerialName("username") val username: String,
)

@Serializable
sealed interface UserEvent

@Serializable
sealed interface RoomJoinRequestEvent: UserEvent {
    @Serializable
    @SerialName("initial")
    data class Initial(
        val requests: List<RoomJoinRequest>,
    ) : RoomJoinRequestEvent

    @Serializable
    @SerialName("added")
    data class Added(
        val request: RoomJoinRequest,
    ) : RoomJoinRequestEvent

    @Serializable
    @SerialName("removed")
    data class Removed(
        @SerialName("request_id")
        val requestId: Long,
    ) : RoomJoinRequestEvent
}

@Serializable
sealed interface UserChatEvent : UserEvent {
    val roomId: Long

    @Serializable
    @SerialName("chat_initial")
    data class Initial(override val roomId: Long, val message: List<Message>) : UserChatEvent

    @Serializable
    @SerialName("chat_added")
    data class Added(override val roomId: Long, val message: Message) : UserChatEvent

    @Serializable
    @SerialName("chat_changed")
    data class Changed(override val roomId: Long, val message: Message) : UserChatEvent

    @Serializable
    @SerialName("chat_deleted")
    data class Deleted(override val roomId: Long, val messageId: Long) : UserChatEvent
}

@Serializable
sealed interface RoomListEvent: UserEvent {
    @Serializable
    @SerialName("rooms_initial")
    data class Initial(val rooms: List<Room>): RoomListEvent

    @Serializable
    @SerialName("room_deleted")
    data class Deleted(val roomId: Long): RoomListEvent

    @Serializable
    @SerialName("room_added")
    data class Added(val room: Room): RoomListEvent

    @Serializable
    @SerialName("room_changed")
    data class Changed(val room: Room): RoomListEvent
}

@Serializable
data class MessagePage(
    @SerialName("messages") val messages: List<Message>,
    @SerialName("hasMore") val hasMore: Boolean,
    @SerialName("userId") val userId: Long,
)

@Serializable
@SerialName("room")
data class Room(
    @SerialName("id") val id: Long,
    @SerialName("name") val name: String,
)

@Serializable
data class RoomMember(
    @SerialName("id") val userId: Long,
    @SerialName("username") val username: String,
    @SerialName("role") val role: RoomRole = RoomRole.Member,
)

@Serializable
enum class RoomRole {
    @SerialName("owner")
    Owner,
    @SerialName("member")
    Member,
}

@Serializable
data class RoomJoinRequest(
    @SerialName("id") val id: Long,
    @SerialName("roomId") val roomId: Long,
    @SerialName("roomName") val roomName: String,
    @SerialName("userId") val userId: Long,
    @SerialName("username") val username: String,
)

@Serializable
data class RoomMembersPage(
    @SerialName("userId") val userId: Long,
    @SerialName("members") val members: List<RoomMember>,
)
