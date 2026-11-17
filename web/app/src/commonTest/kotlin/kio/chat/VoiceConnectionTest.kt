package kio.chat

import kio.chat.domain.*
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.*

class VoiceConnectionTest {
    @Test
    fun onlyTheCallingTabOwnsTheCall() = runBlocking {
        val repo = InMemoryVoiceSignaling()
        val first = repo.openVoiceConnection(1)
        val second = repo.openVoiceConnection(1)
        val peer = repo.openVoiceConnection(2)
        val firstJoin = async(start = CoroutineStart.UNDISPATCHED) { repo.observeVoiceMembershipChanges(first).first() }
        val secondJoin = async(start = CoroutineStart.UNDISPATCHED) { repo.observeVoiceMembershipChanges(second).first() }
        assertTrue(repo.publishVoiceMessage(3, 1, first, VoiceCallMessage.JoinCall))
        assertEquals(3L to VoiceCallMessage.JoinCall, withTimeout(1000) { firstJoin.await() })
        assertFalse(repo.publishVoiceMessage(3, 1, second, VoiceCallMessage.JoinCall))
        assertFalse(repo.publishVoiceMessage(3, 1, peer, VoiceCallMessage.JoinCall))
        assertTrue(repo.publishVoiceMessage(3, 2, peer, VoiceCallMessage.JoinCall))
        assertFalse(secondJoin.isCompleted)
        secondJoin.cancel()
        repo.closeVoiceConnection(second)
        val incoming = async(start = CoroutineStart.UNDISPATCHED) { repo.awaitIncomingOffer(3, peer) }
        val offer = VoiceCallMessage.Offer(2, RtcSessionDescription("offer", "test"))
        assertTrue(repo.publishVoiceMessage(3, 1, first, offer))
        assertEquals(1L to offer, withTimeout(1000) { incoming.await() })
        val leave = async(start = CoroutineStart.UNDISPATCHED) { repo.awaitParticipantLeave(3) }
        repo.closeVoiceConnection(first)
        assertEquals(1L, withTimeout(1000) { leave.await() })
        assertFalse(repo.publishVoiceMessage(3, 1, first, VoiceCallMessage.JoinCall))
        val replacement = repo.openVoiceConnection(1)
        assertTrue(repo.publishVoiceMessage(3, 1, replacement, VoiceCallMessage.JoinCall))
    }
}
