package me.andannn.kio.chat

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import kio.chat.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.webrtc.*
import org.webrtc.audio.JavaAudioDeviceModule

data class VoiceCallState(
    val roomId: Long? = null,
    val muted: Boolean = true,
    val audioDeviceName: String? = null,
)

interface VoiceCallRepository {
    fun CoroutineScope.startObservingEvents(): Job
    val callState: StateFlow<VoiceCallState>
    // The UI obtains microphone permission before joining.
    suspend fun join(roomId: Long): ApiError?
    suspend fun leave(): ApiError?
    suspend fun setMuted(muted: Boolean): ApiError?
}

class VoiceCallRepositoryImpl(
    applicationContext: Context,
    private val service: ChatService,
    private val eventSource: UserEventSource,
) : VoiceCallRepository {
    private val context = applicationContext.applicationContext
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val mutex = Mutex()
    private val state = MutableStateFlow(VoiceCallState())

    override val callState = state.asStateFlow()

    private var call: Call? = null
    private var initialized = false
    private var factory: PeerConnectionFactory? = null
    private var audioDevice: JavaAudioDeviceModule? = null
    private var audioSource: AudioSource? = null
    private var audioTrack: AudioTrack? = null

    private fun observeAudioDeviceName(): Flow<String> = callbackFlow {
        trySend(getCurrentAudioDeviceName())
        val callback = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
                trySend(getCurrentAudioDeviceName())
            }

            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
                trySend(getCurrentAudioDeviceName())
            }
        }
        audioManager.registerAudioDeviceCallback(callback, Handler(Looper.getMainLooper()))
        awaitClose {
            audioManager.unregisterAudioDeviceCallback(callback)
        }
    }.distinctUntilChanged()

    private fun getCurrentAudioDeviceName(): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            audioManager.communicationDevice?.let { return formatAudioDeviceName(it) }
        }
        val inputs = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
        val preferredInput = inputs.firstOrNull { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO }
            ?: inputs.firstOrNull { it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET || it.type == AudioDeviceInfo.TYPE_USB_HEADSET }
            ?: inputs.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_MIC }
            ?: inputs.firstOrNull()
        return preferredInput?.let { formatAudioDeviceName(it) } ?: "麦克风"
    }

    private fun formatAudioDeviceName(device: AudioDeviceInfo): String {
        val productName = device.productName?.toString()?.trim().orEmpty()
        if (productName.isNotEmpty()) return productName
        return when (device.type) {
            AudioDeviceInfo.TYPE_BUILTIN_MIC -> "内置麦克风"
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO, AudioDeviceInfo.TYPE_BLE_HEADSET -> "蓝牙耳机"
            AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_USB_HEADSET -> "有线耳机"
            AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> "听筒"
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "扬声器"
            else -> "音频设备"
        }
    }

    override fun CoroutineScope.startObservingEvents(): Job = launch(start = CoroutineStart.UNDISPATCHED) {
        launch {
            observeAudioDeviceName().collect { name ->
                state.value = state.value.copy(audioDeviceName = name)
            }
        }
        try {
            eventSource.events.collect { event ->
                if (event !is VoiceSignalingEvent && event !is VoiceParticipantsEvent.Removed) return@collect
                try {
                    mutex.withLock {
                        when (event) {
                            is VoiceSignalingEvent -> {
                                call?.takeIf { it.roomId == event.roomId }?.let { handleSignal(it, event, this@launch) }
                            }
                            is VoiceParticipantsEvent.Removed -> {
                                call?.takeIf { it.roomId == event.roomId }?.let { closePeer(it, event.userId) }
                            }

                            else -> {}
                        }
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    Log.e("VoiceCall", "处理语音事件失败", error)
                }
            }
        } finally {
            withContext(NonCancellable) { mutex.withLock { closeLocal() } }
        }
    }

    override suspend fun join(roomId: Long): ApiError? = mutex.withLock {
        val wsSessionId = eventSource.wsSessionId ?: return@withLock ApiError.ServerError("语音连接尚未就绪。")
        if (call?.roomId == roomId) return@withLock null

        leaveInternal()?.let { return@withLock it }

        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            return@withLock ApiError.ServerError("请先授予麦克风权限。")
        }

        val current = Call(roomId, wsSessionId, audioManager.mode)
        call = current
        try {
            openAudio()
            val error = send(current, VoiceCallMessage.JoinCall)
            if (error != null) {
                closeLocal()
                return@withLock error
            }
            state.value = state.value.copy(
                roomId = roomId,
                muted = true,
                audioDeviceName = getCurrentAudioDeviceName(),
            )
            null
        } catch (cancelled: CancellationException) {
            withContext(NonCancellable) { leaveInternal() }
            throw cancelled
        } catch (error: Exception) {
            closeLocal()
            ApiError.ServerError(error.message ?: "加入语音通话失败。", error)
        }
    }

    override suspend fun leave(): ApiError? = mutex.withLock { leaveInternal() }

    override suspend fun setMuted(muted: Boolean): ApiError? = mutex.withLock {
        val track = audioTrack ?: return@withLock ApiError.ServerError("尚未加入语音通话。")
        if (!track.setEnabled(!muted)) return@withLock ApiError.ServerError("无法切换麦克风状态。")
        state.value = state.value.copy(muted = muted)
        null
    }

    private suspend fun handleSignal(call: Call, event: VoiceSignalingEvent, scope: CoroutineScope): ApiError? {
        val peer = createPeer(call, event.peerUserId, scope)
        try {
            return when (event) {
                is VoiceSignalingEvent.OfferRequested -> {
                    val offer = peer.connection.createOfferDescription()
                    peer.connection.setLocalDescription(offer)
                    send(call, VoiceCallMessage.Offer(event.peerUserId, offer))
                }
                is VoiceSignalingEvent.OfferReceived -> {
                    peer.connection.setRemoteDescription(event.offerDescription)
                    flushCandidates(peer)
                    val answer = peer.connection.createAnswerDescription()
                    peer.connection.setLocalDescription(answer)
                    send(call, VoiceCallMessage.Answer(event.peerUserId, answer))
                }
                is VoiceSignalingEvent.AnswerReceived -> {
                    peer.connection.setRemoteDescription(event.answerDescription)
                    flushCandidates(peer)
                    null
                }
                is VoiceSignalingEvent.IceCandidateReceived -> {
                    if (peer.connection.remoteDescription == null) peer.candidates.add(event.candidate)
                    else peer.connection.addIceCandidate(event.candidate)
                    null
                }
            }
        } catch (error: Exception) {
            closePeer(call, event.peerUserId)
            throw error
        }
    }

    private fun createPeer(current: Call, userId: Long, scope: CoroutineScope): Peer = current.peers.getOrPut(userId) {
        lateinit var connection: PeerConnection
        val observer = peerConnectionObserver(
            onCandidate = { candidate ->
                scope.launch {
                    mutex.withLock {
                        if (current.peers[userId]?.connection === connection) {
                            send(current, VoiceCallMessage.IceCandidate(userId, candidate))?.let { Log.w("VoiceCall", "$it") }
                        }
                    }
                }
            },
            onStateChanged = { connectionState ->
                if (connectionState == PeerConnection.PeerConnectionState.FAILED) scope.launch {
                    mutex.withLock {
                        if (current.peers[userId]?.connection === connection) closePeer(current, userId)
                    }
                }
            },
        )
        val config = PeerConnection.RTCConfiguration(
            listOf(
                PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
            )
        ).apply { sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN }

        connection = checkNotNull(checkNotNull(factory).createPeerConnection(config, observer))

        try {
            checkNotNull(connection.addTrack(checkNotNull(audioTrack), listOf("voice")))
            Peer(connection)
        } catch (error: Exception) {
            connection.dispose()
            throw error
        }
    }

    private fun flushCandidates(peer: Peer) {
        peer.candidates.forEach { peer.connection.addIceCandidate(it) }
        peer.candidates.clear()
    }

    private fun closePeer(current: Call, userId: Long) {
        current.peers.remove(userId)?.connection?.dispose()
    }

    private suspend fun send(current: Call, message: VoiceCallMessage): ApiError? {
        if (!eventSource.isActiveSession(current.wsSessionId)) return ApiError.ServerError("语音连接已断开。")
        return service.sendVoiceMessage(current.roomId, VoiceCallRequest(current.wsSessionId, message)).error
    }

    private suspend fun leaveInternal(): ApiError? {
        val current = call ?: return null
        closeLocal()
        return send(current, VoiceCallMessage.LeaveCall)
    }

    private fun openAudio() {
        if (!initialized) {
            PeerConnectionFactory.initialize(PeerConnectionFactory.InitializationOptions.builder(context).createInitializationOptions())
            initialized = true
        }
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        val device = JavaAudioDeviceModule.builder(context).createAudioDeviceModule().also { audioDevice = it }
        val peerFactory = PeerConnectionFactory.builder().setAudioDeviceModule(device).createPeerConnectionFactory().also { factory = it }
        val source = peerFactory.createAudioSource(MediaConstraints()).also { audioSource = it }
        audioTrack = peerFactory.createAudioTrack("voice", source).also { it.setEnabled(false) }
    }

    private fun closeLocal() {
        val current = call ?: return
        call = null
        current.peers.keys.toList().forEach { closePeer(current, it) }
        audioTrack?.dispose()
        audioTrack = null
        audioSource?.dispose()
        audioSource = null
        factory?.dispose()
        factory = null
        audioDevice?.release()
        audioDevice = null
        audioManager.mode = current.previousAudioMode
        state.value = VoiceCallState()
    }
}

private data class Peer(val connection: PeerConnection, val candidates: MutableList<RtcIceCandidate> = mutableListOf())

private data class Call(
    val roomId: Long,
    val wsSessionId: String,
    val previousAudioMode: Int,
    val peers: MutableMap<Long, Peer> = mutableMapOf(),
)