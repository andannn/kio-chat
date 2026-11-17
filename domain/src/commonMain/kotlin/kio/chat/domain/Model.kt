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
sealed interface VoiceSignalingEvent: UserEvent {
    @Serializable
    @SerialName("voice_connection_ready")
    data class ConnectionReady(val connectionId: String) : VoiceSignalingEvent

    @Serializable
    @SerialName("on_request_send_offer_to_user")
    data class OfferRequested(val roomId: Long, @SerialName("userId") val peerUserId: Long): VoiceSignalingEvent

    @Serializable
    @SerialName("on_request_send_answer_to_user")
    data class OfferReceived(val roomId: Long, @SerialName("userId") val peerUserId: Long, val offerDescription: RtcSessionDescription): VoiceSignalingEvent

    @Serializable
    @SerialName("on_request_save_answer")
    data class AnswerReceived(val roomId: Long, @SerialName("userId") val peerUserId: Long, val answerDescription: RtcSessionDescription): VoiceSignalingEvent

    @Serializable
    @SerialName("on_receive_candidate")
    data class IceCandidateReceived(val roomId: Long, @SerialName("userId") val peerUserId: Long, val candidate: RtcIceCandidate): VoiceSignalingEvent
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

@Serializable(with = VoiceCallMessageSerializer::class)
sealed interface VoiceCallMessage {
    sealed interface MembershipChange: VoiceCallMessage

    @Serializable
    @SerialName("user_joined")
    data object JoinCall: MembershipChange

    @Serializable
    @SerialName("user_exit")
    data object LeaveCall: MembershipChange

    sealed interface PeerSignal: VoiceCallMessage {
        val recipientUserId: Long
    }

    @Serializable
    @SerialName("rtc_conn_offer")
    data class Offer(@SerialName("toUserId") override val recipientUserId: Long, @SerialName("description") val description: RtcSessionDescription): PeerSignal

    @Serializable
    @SerialName("rtc_conn_answer")
    data class Answer(@SerialName("toUserId") override val recipientUserId: Long, @SerialName("description") val description: RtcSessionDescription): PeerSignal

    @Serializable
    @SerialName("rtc_conn_candidate")
    data class IceCandidate(@SerialName("toUserId") override val recipientUserId: Long, @SerialName("candidate") val iceCandidate: RtcIceCandidate): PeerSignal
}

object VoiceCallMessageSerializer : KSerializer<VoiceCallMessage> {

    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("VoiceCallMessage")

    override fun deserialize(decoder: Decoder): VoiceCallMessage {
        require(decoder is JsonDecoder)

        val element = decoder.decodeJsonElement()
        val type = element.jsonObject["type"]?.jsonPrimitive?.content
            ?: throw SerializationException("Missing event type")

        val serializer = when (type) {
            "user_joined" -> VoiceCallMessage.JoinCall.serializer()
            "user_exit" -> VoiceCallMessage.LeaveCall.serializer()
            "rtc_conn_offer" -> VoiceCallMessage.Offer.serializer()
            "rtc_conn_answer" -> VoiceCallMessage.Answer.serializer()
            "rtc_conn_candidate" -> VoiceCallMessage.IceCandidate.serializer()
            else -> throw SerializationException("Unknown event type: $type")
        }

        return decoder.json.decodeFromJsonElement(serializer, JsonObject(element.jsonObject - "type"))
    }

    override fun serialize(encoder: Encoder, value: VoiceCallMessage) {
        require(encoder is JsonEncoder)

        val (type, element) = when (value) {
            is VoiceCallMessage.JoinCall ->
                "user_joined" to encoder.json.encodeToJsonElement(
                    VoiceCallMessage.JoinCall.serializer(), value
                )
            is VoiceCallMessage.LeaveCall ->
                "user_exit" to encoder.json.encodeToJsonElement(
                    VoiceCallMessage.LeaveCall.serializer(), value
                )
            is VoiceCallMessage.Offer ->
                "rtc_conn_offer" to encoder.json.encodeToJsonElement(
                    VoiceCallMessage.Offer.serializer(), value
                )
            is VoiceCallMessage.Answer ->
                "rtc_conn_answer" to encoder.json.encodeToJsonElement(
                    VoiceCallMessage.Answer.serializer(), value
                )
            is VoiceCallMessage.IceCandidate ->
                "rtc_conn_candidate" to encoder.json.encodeToJsonElement(
                    VoiceCallMessage.IceCandidate.serializer(), value
                )
        }

        encoder.encodeJsonElement(
            JsonObject(element.jsonObject + ("type" to JsonPrimitive(type)))
        )
    }
}

@Serializable
data class RtcSessionDescription(
    val type: String,
    val sdp: String,
)

@Serializable
data class RtcIceCandidate(
    val candidate: String,
    val sdpMid: String? = null,
    val sdpMLineIndex: Int? = null,
    val usernameFragment: String? = null,
)