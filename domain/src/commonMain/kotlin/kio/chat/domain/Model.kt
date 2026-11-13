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
sealed interface MessageEvent {
    @Serializable
    @SerialName("initial")
    data class Initial(val message: List<Message>): MessageEvent
    @Serializable
    @SerialName("added")
    data class Added(val message: Message) : MessageEvent
    @Serializable
    @SerialName("changed")
    data class Changed(val message: Message) : MessageEvent
    @Serializable
    @SerialName("deleted")
    data class Deleted(val messageId: Long) : MessageEvent
}
