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
    @SerialName("nickname") val nickname: PgText,
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
    @SerialName("room_id")
    val roomId: PgInt8,
    @SerialName("msg_type")
    val msgType: PgText = "text",
    @SerialName("extra")
    val extra: PgText? = null,
)

@Serializable
data class ChatMessageNotification(
    @SerialName("operation") val operation: String,
    @SerialName("message_id") val messageId: Long,
    @SerialName("room_id") val roomId: Long,
)

@Serializable
data class ChatRoomEntity(
    @SerialName("id") val id: PgInt8,
    @SerialName("name") val name: PgText,
    @SerialName("create_at") val createAt: PgTimestampTz,
)

@Serializable
data class ChatRoomMemberEntity(
    @SerialName("id") val userId: PgInt8,
    @SerialName("nickname") val nickname: PgText,
    @SerialName("role") val role: PgText,
)

@Serializable
data class ChatRoomJoinRequestEntity(
    @SerialName("id") val id: PgInt8,
    @SerialName("room_id") val roomId: PgInt8,
    @SerialName("user_id") val userId: PgInt8,
    @SerialName("create_at") val createAt: PgTimestampTz,
)

@Serializable
data class ChatRoomJoinRequestWithUserEntity(
    @SerialName("id") val id: PgInt8,
    @SerialName("room_id") val roomId: PgInt8,
    @SerialName("room_name") val roomName: PgText,
    @SerialName("user_id") val userId: PgInt8,
    @SerialName("nickname") val nickname: PgText,
    @SerialName("create_at") val createAt: PgTimestampTz,
)

@Serializable
data class ChatRoomJoinRequestNotification(
    @SerialName("request_id") val requestId: Long,
    val operation: String,
    @SerialName("request_room_id") val requestRoomId: Long,
    @SerialName("request_user_id") val requestUserId: Long,
)

@Serializable
data class ChatRoomMemberNotification(
    @SerialName("operation") val operation: String,
    @SerialName("room_id") val roomId: Long,
    @SerialName("user_id") val userId: Long,
)

@Serializable
data class ChatRoomNotification(
    @SerialName("operation") val operation: String,
    @SerialName("room_id") val roomId: Long,
)
