package kio.chat.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.uuid.Uuid

interface VoiceSignaling {
    suspend fun openVoiceConnection(userId: Long): String
    suspend fun closeVoiceConnection(connectionId: String)
    // Callers must obtain senderUserId from the authenticated session.
    suspend fun publishVoiceMessage(roomId: Long, senderUserId: Long, connectionId: String, message: VoiceCallMessage): Boolean
    // Emits the room ID and the join or leave message.
    fun observeVoiceMembershipChanges(connectionId: String): Flow<Pair<Long, VoiceCallMessage.MembershipChange>>
    suspend fun awaitParticipantJoin(roomId: Long): Long
    suspend fun awaitParticipantLeave(roomId: Long): Long
    suspend fun awaitAnswerFrom(roomId: Long, recipientConnectionId: String, senderUserId: Long): VoiceCallMessage.Answer
    // Returns the sender ID and offer.
    suspend fun awaitIncomingOffer(roomId: Long, recipientConnectionId: String): Pair<Long, VoiceCallMessage.Offer>
    // Returns the sender ID and ICE candidate.
    suspend fun awaitIncomingIceCandidate(roomId: Long, recipientConnectionId: String): Pair<Long, VoiceCallMessage.IceCandidate>
}

class InMemoryVoiceSignaling: VoiceSignaling {
    private val voiceEvents = MutableSharedFlow<VoiceMessageEnvelope>(extraBufferCapacity = 64)

    private val membershipEvents = voiceEvents.filterIsInstance<VoiceMessageEnvelope.Membership>()
    private val peerSignals = voiceEvents.filterIsInstance<VoiceMessageEnvelope.PeerSignal>()

    private data class Connection(val userId: Long, var roomId: Long? = null)

    private val mutex = Mutex()

    // connection_id to connection
    private val connections = mutableMapOf<String, Connection>()

    // user_id to connection_id
    private val activeConnections = mutableMapOf<Long, String>()

    override suspend fun openVoiceConnection(userId: Long): String = mutex.withLock {
        Uuid.random().toString().also { connections[it] = Connection(userId) }
    }

    override suspend fun closeVoiceConnection(connectionId: String) = mutex.withLock {
        val connection = connections.remove(connectionId) ?: return@withLock
        val roomId = connection.roomId ?: return@withLock
        activeConnections.remove(connection.userId)
        voiceEvents.emit(VoiceMessageEnvelope.Membership(roomId, connection.userId, connectionId, VoiceCallMessage.LeaveCall))
    }

    override suspend fun publishVoiceMessage(
        roomId: Long, senderUserId: Long, connectionId: String, message: VoiceCallMessage,
    ): Boolean = mutex.withLock {
        val connection = connections[connectionId] ?: return false
        if (connection.userId != senderUserId) return false
        withContext(NonCancellable) {
            val event = when (message) {
                VoiceCallMessage.JoinCall -> {
                    val owner = activeConnections[senderUserId]
                    if (owner != null && owner != connectionId) return@withContext false
                    val previousRoom = connection.roomId
                    if (previousRoom == roomId) return@withContext true
                    if (previousRoom != null) {
                        voiceEvents.emit(VoiceMessageEnvelope.Membership(previousRoom, senderUserId, connectionId, VoiceCallMessage.LeaveCall))
                    }
                    activeConnections[senderUserId] = connectionId
                    connection.roomId = roomId
                    VoiceMessageEnvelope.Membership(roomId, senderUserId, connectionId, VoiceCallMessage.JoinCall)
                }
                VoiceCallMessage.LeaveCall -> {
                    if (connection.roomId != roomId) return@withContext true
                    activeConnections.remove(senderUserId)
                    connection.roomId = null
                    VoiceMessageEnvelope.Membership(roomId, senderUserId, connectionId, VoiceCallMessage.LeaveCall)
                }
                is VoiceCallMessage.PeerSignal -> {
                    val recipient = activeConnections[message.recipientUserId] ?: return@withContext false
                    if (connection.roomId != roomId || connections[recipient]?.roomId != roomId) return@withContext false
                    VoiceMessageEnvelope.PeerSignal(roomId, senderUserId, recipient, message)
                }
            }
            voiceEvents.emit(event)
            true
        }
    }

    override fun observeVoiceMembershipChanges(connectionId: String): Flow<Pair<Long, VoiceCallMessage.MembershipChange>> =
        membershipEvents.filter { it.senderConnectionId == connectionId }.map { it.roomId to it.message }

    override suspend fun awaitParticipantJoin(roomId: Long): Long =
        membershipEvents.first { it.message is VoiceCallMessage.JoinCall && it.roomId == roomId }.senderUserId

    override suspend fun awaitParticipantLeave(roomId: Long): Long =
        membershipEvents.first { it.message is VoiceCallMessage.LeaveCall && it.roomId == roomId }.senderUserId

    override suspend fun awaitAnswerFrom(roomId: Long, recipientConnectionId: String, senderUserId: Long): VoiceCallMessage.Answer =
        peerSignals.first {
            it.message is VoiceCallMessage.Answer && it.roomId == roomId &&
                    it.recipientConnectionId == recipientConnectionId && it.senderUserId == senderUserId
        }.let { it.message as VoiceCallMessage.Answer }

    override suspend fun awaitIncomingOffer(roomId: Long, recipientConnectionId: String): Pair<Long, VoiceCallMessage.Offer> =
        peerSignals.first {
            val message = it.message
            message is VoiceCallMessage.Offer && it.roomId == roomId && it.recipientConnectionId == recipientConnectionId
        }.let { it.senderUserId to it.message as VoiceCallMessage.Offer }

    override suspend fun awaitIncomingIceCandidate(roomId: Long, recipientConnectionId: String): Pair<Long, VoiceCallMessage.IceCandidate> =
        peerSignals.first {
            val message = it.message
            message is VoiceCallMessage.IceCandidate && it.roomId == roomId && it.recipientConnectionId == recipientConnectionId
        }.let { it.senderUserId to it.message as VoiceCallMessage.IceCandidate }
}

private sealed interface VoiceMessageEnvelope {
    val roomId: Long
    val senderUserId: Long

    data class Membership(
        override val roomId: Long,
        override val senderUserId: Long,
        val senderConnectionId: String,
        val message: VoiceCallMessage.MembershipChange,
    ) : VoiceMessageEnvelope

    data class PeerSignal(
        override val roomId: Long,
        override val senderUserId: Long,
        val recipientConnectionId: String,
        val message: VoiceCallMessage.PeerSignal,
    ) : VoiceMessageEnvelope
}
