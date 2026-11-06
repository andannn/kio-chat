package kio.chat.database

import kio.postgres.types.PgInt8
import kio.postgres.types.PgText
import kio.postgres.types.PgTimestampTz
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ChatUserEntity(
    @SerialName("id")
    val id: PgInt8,
    @SerialName("username")
    val username: PgText,
    @SerialName("password_hash")
    val passwordHash: PgText,
    @SerialName("create_at")
    val createAt: PgTimestampTz,
)

@Serializable
data class ChatSessionEntity(
    @SerialName("id")
    val id: PgText,
    @SerialName("user_id")
    val userId: PgInt8,
    @SerialName("create_at")
    val createAt: PgTimestampTz,
)


@Serializable
data class ChatMessageEntity(
    @SerialName("id")
    val id: PgInt8,
    @SerialName("user_id")
    val userId: PgInt8,
    @SerialName("text")
    val text: PgText,
    @SerialName("create_at")
    val createAt: PgTimestampTz,
)

@Serializable
data class ChatMessageNotification(
    val operation: String,
    @SerialName("message_id") val messageId: Long,
)
