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
sealed interface RoomJoinRequestEvent {
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
sealed interface ChatEvent {
    @Serializable
    @SerialName("initial")
    data class Initial(val message: List<Message>) : ChatEvent

    @Serializable
    @SerialName("added")
    data class Added(val message: Message) : ChatEvent

    @Serializable
    @SerialName("changed")
    data class Changed(val message: Message) : ChatEvent

    @Serializable
    @SerialName("deleted")
    data class Deleted(val messageId: Long) : ChatEvent
}

@Serializable
@SerialName("message_pack")
data class MessagePack(
    @SerialName("userId") val userId: Long,
    @SerialName("event") val event: ChatEvent,
)

@Serializable
data class MessagePage(
    @SerialName("messages") val messages: List<Message>,
    @SerialName("hasMore") val hasMore: Boolean,
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
