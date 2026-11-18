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

interface WsSessionRepository {
    suspend fun openWsSession(userId: Long): String
    suspend fun closeWsSession(wsSessionId: String)
    suspend fun getVoiceParticipantIds(roomId: Long): Set<Long>
    fun observeVoiceParticipantChanges(): Flow<VoiceParticipantChange>
    // Callers must obtain senderUserId from the authenticated session.
    suspend fun publishVoiceMessage(roomId: Long, senderUserId: Long, wsSessionId: String, message: VoiceCallMessage): Boolean
    // Emits the room ID and the join or leave message.
    fun observeVoiceMembershipChanges(wsSessionId: String): Flow<Pair<Long, VoiceCallMessage.MembershipChange>>
    suspend fun awaitParticipantJoin(roomId: Long): Long
    fun observeParticipantLeave(roomId: Long): Flow<Long>
    suspend fun awaitAnswerFrom(roomId: Long, recipientWsSessionId: String, senderUserId: Long): VoiceCallMessage.Answer
    // Returns the sender ID and offer.
    fun observeIncomingOffer(roomId: Long, recipientWsSessionId: String): Flow<Pair<Long, VoiceCallMessage.Offer>>
    // Returns the sender ID and ICE candidate.
    fun observeIncomingIceCandidate(roomId: Long, recipientWsSessionId: String): Flow<Pair<Long, VoiceCallMessage.IceCandidate>>
}

class InMemoryWsSessionRepository: WsSessionRepository {
    private val wsSessionEvents = MutableSharedFlow<WsSessionEvent>(extraBufferCapacity = 64)

    private val membershipEvents = wsSessionEvents.filterIsInstance<WsSessionEvent.Membership>()
    private val peerSignals = wsSessionEvents.filterIsInstance<WsSessionEvent.PeerSignal>()

    private data class WsSession(val userId: Long, var roomId: Long? = null)

    private val mutex = Mutex()

    // ws_session_id to wsSession
    private val wsSessions = mutableMapOf<String, WsSession>()

    // Must be called while holding mutex.
    private fun findActiveWsSessionId(userId: Long): String? =
        wsSessions.entries.firstOrNull {
            it.value.userId == userId && it.value.roomId != null
        }?.key

    override suspend fun getVoiceParticipantIds(roomId: Long): Set<Long> = mutex.withLock {
        wsSessions.values.filter { it.roomId == roomId }.map { it.userId }.toSet()
    }

    override fun observeVoiceParticipantChanges(): Flow<VoiceParticipantChange> =
        membershipEvents.map {
            VoiceParticipantChange(it.roomId, it.senderUserId, it.message is VoiceCallMessage.JoinCall)
        }

    override suspend fun openWsSession(userId: Long): String = mutex.withLock {
        Uuid.random().toString().also { wsSessions[it] = WsSession(userId) }
    }

    override suspend fun closeWsSession(wsSessionId: String) = mutex.withLock {
        val wsSession = wsSessions.remove(wsSessionId) ?: return@withLock
        val roomId = wsSession.roomId ?: return@withLock
        wsSessionEvents.emit(WsSessionEvent.Membership(roomId, wsSession.userId, wsSessionId, VoiceCallMessage.LeaveCall))
    }

    override suspend fun publishVoiceMessage(
        roomId: Long, senderUserId: Long, wsSessionId: String, message: VoiceCallMessage,
    ): Boolean = mutex.withLock {
        val wsSession = wsSessions[wsSessionId] ?: return false
        if (wsSession.userId != senderUserId) return false
        withContext(NonCancellable) {
            val event = when (message) {
                VoiceCallMessage.JoinCall -> {
                    val owner = findActiveWsSessionId(senderUserId)
                    if (owner != null && owner != wsSessionId) return@withContext false
                    val previousRoom = wsSession.roomId
                    if (previousRoom == roomId) return@withContext true
                    if (previousRoom != null) {
                        wsSessionEvents.emit(WsSessionEvent.Membership(previousRoom, senderUserId, wsSessionId, VoiceCallMessage.LeaveCall))
                    }
                    wsSession.roomId = roomId
                    WsSessionEvent.Membership(roomId, senderUserId, wsSessionId, VoiceCallMessage.JoinCall)
                }
                VoiceCallMessage.LeaveCall -> {
                    if (wsSession.roomId != roomId) return@withContext true
                    wsSession.roomId = null
                    WsSessionEvent.Membership(roomId, senderUserId, wsSessionId, VoiceCallMessage.LeaveCall)
                }
                is VoiceCallMessage.PeerSignal -> {
                    val recipient = findActiveWsSessionId(message.recipientUserId) ?: return@withContext false
                    if (wsSession.roomId != roomId || wsSessions[recipient]?.roomId != roomId) return@withContext false
                    WsSessionEvent.PeerSignal(roomId, senderUserId, recipient, message)
                }
            }
            wsSessionEvents.emit(event)
            true
        }
    }

    override fun observeVoiceMembershipChanges(wsSessionId: String): Flow<Pair<Long, VoiceCallMessage.MembershipChange>> =
        membershipEvents.filter { it.senderWsSessionId == wsSessionId }.map { it.roomId to it.message }

    override suspend fun awaitParticipantJoin(roomId: Long): Long =
        membershipEvents.first { it.message is VoiceCallMessage.JoinCall && it.roomId == roomId }.senderUserId

    override fun observeParticipantLeave(roomId: Long): Flow<Long> =
        membershipEvents.filter { it.message is VoiceCallMessage.LeaveCall && it.roomId == roomId }.map { it.senderUserId }

    override suspend fun awaitAnswerFrom(roomId: Long, recipientWsSessionId: String, senderUserId: Long): VoiceCallMessage.Answer =
        peerSignals.first {
            it.message is VoiceCallMessage.Answer && it.roomId == roomId &&
                    it.recipientWsSessionId == recipientWsSessionId && it.senderUserId == senderUserId
        }.let { it.message as VoiceCallMessage.Answer }

    override fun observeIncomingOffer(roomId: Long, recipientWsSessionId: String): Flow<Pair<Long, VoiceCallMessage.Offer>> =
        peerSignals.filter {
            val message = it.message
            message is VoiceCallMessage.Offer && it.roomId == roomId && it.recipientWsSessionId == recipientWsSessionId
        }.map { it.senderUserId to it.message as VoiceCallMessage.Offer }

    override fun observeIncomingIceCandidate(roomId: Long, recipientWsSessionId: String): Flow<Pair<Long, VoiceCallMessage.IceCandidate>> =
        peerSignals.filter {
            val message = it.message
            message is VoiceCallMessage.IceCandidate && it.roomId == roomId && it.recipientWsSessionId == recipientWsSessionId
        }.map { it.senderUserId to it.message as VoiceCallMessage.IceCandidate }
}

data class VoiceParticipantChange(
    val roomId: Long,
    val userId: Long,
    val joined: Boolean,
)

private sealed interface WsSessionEvent {
    val roomId: Long
    val senderUserId: Long

    data class Membership(
        override val roomId: Long,
        override val senderUserId: Long,
        val senderWsSessionId: String,
        val message: VoiceCallMessage.MembershipChange,
    ) : WsSessionEvent

    data class PeerSignal(
        override val roomId: Long,
        override val senderUserId: Long,
        val recipientWsSessionId: String,
        val message: VoiceCallMessage.PeerSignal,
    ) : WsSessionEvent
}
