package kio.chat.domain

data class User(val id: Long, val username: String, val passwordHash: String)
data class Session(val userId: Long)

data class Message(val id: Long, val user: User, val text: String, val username: String)

sealed interface MessageEvent {
    data class Initial(val message: List<Message>): MessageEvent
    data class Added(val message: Message) : MessageEvent
    data class Changed(val message: Message) : MessageEvent
    data class Deleted(val messageId: Long) : MessageEvent
}
