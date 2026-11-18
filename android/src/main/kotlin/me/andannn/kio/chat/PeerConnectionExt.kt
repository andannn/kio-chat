package me.andannn.kio.chat

import kio.chat.domain.RtcIceCandidate
import kio.chat.domain.RtcSessionDescription
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import org.webrtc.DataChannel
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.time.Duration.Companion.seconds

internal suspend fun PeerConnection.createOfferDescription(): RtcSessionDescription = createDescription(offer = true)
internal suspend fun PeerConnection.createAnswerDescription(): RtcSessionDescription = createDescription(offer = false)

private suspend fun PeerConnection.createDescription(offer: Boolean): RtcSessionDescription = withTimeout(10.seconds) {
    suspendCancellableCoroutine { continuation ->
        val observer = object : SdpObserver {
            override fun onCreateSuccess(description: SessionDescription) {
                if (continuation.isActive) continuation.resume(RtcSessionDescription(description.type.canonicalForm(), description.description))
            }
            override fun onCreateFailure(error: String) {
                if (continuation.isActive) continuation.resumeWithException(IllegalStateException(error))
            }
            override fun onSetSuccess() = Unit
            override fun onSetFailure(error: String) = Unit
        }
        if (offer) createOffer(observer, MediaConstraints()) else createAnswer(observer, MediaConstraints())
    }
}

internal suspend fun PeerConnection.setLocalDescription(description: RtcSessionDescription) = setDescription(description, remote = false)
internal suspend fun PeerConnection.setRemoteDescription(description: RtcSessionDescription) = setDescription(description, remote = true)

private suspend fun PeerConnection.setDescription(description: RtcSessionDescription, remote: Boolean) = withTimeout(10.seconds) {
    suspendCancellableCoroutine<Unit> { continuation ->
        val observer = object : SdpObserver {
            override fun onSetSuccess() { if (continuation.isActive) continuation.resume(Unit) }
            override fun onSetFailure(error: String) {
                if (continuation.isActive) continuation.resumeWithException(IllegalStateException(error))
            }
            override fun onCreateSuccess(description: SessionDescription) = Unit
            override fun onCreateFailure(error: String) = Unit
        }
        val native = SessionDescription(SessionDescription.Type.fromCanonicalForm(description.type), description.sdp)
        if (remote) setRemoteDescription(observer, native) else setLocalDescription(observer, native)
    }
}

internal fun PeerConnection.addIceCandidate(candidate: RtcIceCandidate) {
    if (candidate.candidate.isEmpty()) return
    require(candidate.sdpMid != null || candidate.sdpMLineIndex != null) { "ICE candidate 缺少媒体标识。" }
    check(addIceCandidate(IceCandidate(candidate.sdpMid, candidate.sdpMLineIndex ?: 0, candidate.candidate))) {
        "添加 ICE candidate 失败。"
    }
}

internal fun peerConnectionObserver(
    onCandidate: (RtcIceCandidate) -> Unit,
    onStateChanged: (PeerConnection.PeerConnectionState) -> Unit,
): PeerConnection.Observer = object : PeerConnection.Observer {
    override fun onIceCandidate(candidate: IceCandidate) = onCandidate(RtcIceCandidate(candidate.sdp, candidate.sdpMid, candidate.sdpMLineIndex))
    override fun onConnectionChange(state: PeerConnection.PeerConnectionState) = onStateChanged(state)
    override fun onSignalingChange(state: PeerConnection.SignalingState) = Unit
    override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) = Unit
    override fun onIceConnectionReceivingChange(receiving: Boolean) = Unit
    override fun onIceGatheringChange(state: PeerConnection.IceGatheringState) = Unit
    override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>) = Unit
    override fun onAddStream(stream: MediaStream) = Unit
    override fun onRemoveStream(stream: MediaStream) = Unit
    override fun onDataChannel(channel: DataChannel) = Unit
    override fun onRenegotiationNeeded() = Unit
}
