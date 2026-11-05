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

