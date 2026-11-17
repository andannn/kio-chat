package kio.chat.domain

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Serializable
@SerialName("user")
data class User(
    @SerialName("id") val id: Long,
    @SerialName("username") val username: String,
    @SerialName("passwordHash") val passwordHash: String,
    @SerialName("nickname") val nickname: String = username,
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
    @SerialName("nickname") val nickname: String,
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
sealed interface RoomVoiceEvent: UserEvent {
    @Serializable
    @SerialName("on_request_send_offer_to_user")
    data class OnRequestSendOfferToUser(val roomId: Long, val userId: Long): RoomVoiceEvent

    @Serializable
    @SerialName("on_request_send_answer_to_user")
    data class OnRequestSendAnswerToUser(val roomId: Long, val userId: Long, val offerDescription: RTCSessionDescription): RoomVoiceEvent

    @Serializable
    @SerialName("on_request_save_answer")
    data class OnRequestSaveAnswer(val roomId: Long, val userId: Long, val answerDescription: RTCSessionDescription): RoomVoiceEvent

    @Serializable
    @SerialName("on_receive_candidate")
    data class OnReceiveCandidate(val roomId: Long, val userId: Long, val candidate: RTCIceCandidate): RoomVoiceEvent
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
    @SerialName("nickname") val nickname: String,
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
    @SerialName("nickname") val nickname: String,
)

@Serializable
data class RoomMembersPage(
    @SerialName("userId") val userId: Long,
    @SerialName("members") val members: List<RoomMember>,
)

@Serializable
data class AppVersion(
    @SerialName("versionCode") val versionCode: Int,
    @SerialName("versionName") val versionName: String,
)

@Serializable(with = RoomMessageSerializer::class)
sealed interface RoomMessage {
    sealed interface Voice: RoomMessage

    @Serializable
    @SerialName("user_joined")
    data object VoiceJoin: Voice

    @Serializable
    @SerialName("user_exit")
    data object VoiceLeave: Voice

    sealed interface Signaling: RoomMessage {
        val toUserId: Long
    }

    @Serializable
    @SerialName("rtc_conn_offer")
    data class RTCOffer(override val toUserId: Long, @SerialName("description") val description: RTCSessionDescription): Signaling

    @Serializable
    @SerialName("rtc_conn_answer")
    data class RTCAnswer(override val toUserId: Long, @SerialName("description") val description: RTCSessionDescription): Signaling

    @Serializable
    @SerialName("rtc_conn_candidate")
    data class RTCCandidate(override val toUserId: Long, @SerialName("candidate") val iceCandidate: RTCIceCandidate): Signaling
}

object RoomMessageSerializer : KSerializer<RoomMessage> {

    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("RoomMessage")

    override fun deserialize(decoder: Decoder): RoomMessage {
        require(decoder is JsonDecoder)

        val element = decoder.decodeJsonElement()
        val type = element.jsonObject["type"]?.jsonPrimitive?.content
            ?: throw SerializationException("Missing event type")

        val serializer = when (type) {
            "user_joined" -> RoomMessage.VoiceJoin.serializer()
            "user_exit" -> RoomMessage.VoiceLeave.serializer()
            "rtc_conn_offer" -> RoomMessage.RTCOffer.serializer()
            "rtc_conn_answer" -> RoomMessage.RTCAnswer.serializer()
            "rtc_conn_candidate" -> RoomMessage.RTCCandidate.serializer()
            else -> throw SerializationException("Unknown event type: $type")
        }

        return decoder.json.decodeFromJsonElement(serializer, JsonObject(element.jsonObject - "type"))
    }

    override fun serialize(encoder: Encoder, value: RoomMessage) {
        require(encoder is JsonEncoder)

        val (type, element) = when (value) {
            is RoomMessage.VoiceJoin ->
                "user_joined" to encoder.json.encodeToJsonElement(
                    RoomMessage.VoiceJoin.serializer(), value
                )
            is RoomMessage.VoiceLeave ->
                "user_exit" to encoder.json.encodeToJsonElement(
                    RoomMessage.VoiceLeave.serializer(), value
                )
            is RoomMessage.RTCOffer ->
                "rtc_conn_offer" to encoder.json.encodeToJsonElement(
                    RoomMessage.RTCOffer.serializer(), value
                )
            is RoomMessage.RTCAnswer ->
                "rtc_conn_answer" to encoder.json.encodeToJsonElement(
                    RoomMessage.RTCAnswer.serializer(), value
                )
            is RoomMessage.RTCCandidate ->
                "rtc_conn_candidate" to encoder.json.encodeToJsonElement(
                    RoomMessage.RTCCandidate.serializer(), value
                )
        }

        encoder.encodeJsonElement(
            JsonObject(element.jsonObject + ("type" to JsonPrimitive(type)))
        )
    }
}

@Serializable
data class RTCSessionDescription(
    val type: String,
    val sdp: String,
)

@Serializable
data class RTCIceCandidate(
    val candidate: String,
    val sdpMid: String? = null,
    val sdpMLineIndex: Int? = null,
    val usernameFragment: String? = null,
)