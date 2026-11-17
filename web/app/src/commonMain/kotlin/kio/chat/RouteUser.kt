package kio.chat

import kio.chat.components.*
import kio.chat.domain.Repository
import kio.chat.domain.VoiceCallMessage
import kio.chat.domain.VoiceSignalingEvent
import kio.chat.domain.RoomJoinRequestEvent
import kio.chat.domain.RoomListEvent
import kio.chat.domain.UserChatEvent
import kio.chat.domain.UserEvent
import kio.http.WebsocketContext
import kio.http.currentLogger
import kio.http.info
import kio.http.sendText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.html.stream.createHTML
import kotlinx.serialization.json.Json
import kotlin.time.Duration.Companion.seconds

context(repo: Repository)
internal suspend fun WebsocketContext.handleObserveUserEvent() = coroutineScope {
    val myId = requireSession().userId
    val isJson = call.requestParameters["format"] == "json"
    val selectedRoomId = call.requestParameters["selectedRoomId"]?.toLongOrNull()
    currentLogger().info("[voice] WebSocket connected: user=$myId")

    launch {
        val connectionId = repo.openVoiceConnection(myId)
        try {
            coroutineScope {
                forwardVoiceSignaling(this@handleObserveUserEvent, myId, connectionId)
                sendText(Json.encodeToString<UserEvent>(VoiceSignalingEvent.ConnectionReady(connectionId)))
            }
        } finally {
            withContext(NonCancellable) { repo.closeVoiceConnection(connectionId) }
        }
    }
    handlingRoomJoinRequest(this@handleObserveUserEvent, myId, isJson)
    handlingRoomChatEvent(this@handleObserveUserEvent, myId, isJson, selectedRoomId)
}

context(repo: Repository)
private fun CoroutineScope.handlingRoomChatEvent(
    ws: WebsocketContext,
    myId: Long,
    isJson: Boolean,
    selectedRoomId: Long?
) = launch {
    repo.observeRoomAndChatMessageEventByUser(myId).collect { event ->
        if (isJson) {
            ws.sendText(Json.encodeToString<UserEvent>(event))
        } else {
            ws.sendText(createHTML().apply {
                when (event) {
                    is RoomListEvent -> {
                        when (event) {
                            is RoomListEvent.Initial -> roomListInitial(event.rooms, selectedRoomId)
                            is RoomListEvent.Added -> roomListAdded(event.room)
                            is RoomListEvent.Deleted -> roomListDeleted(event.roomId)
                            is RoomListEvent.Changed -> roomListChanged(event.room)
                        }
                    }
                    is UserChatEvent -> chatMessageEvent(event, myId)
                    else -> Unit
                }
            }.finalize())
        }
    }
}

context(repo: Repository)
private fun CoroutineScope.handlingRoomJoinRequest(ws: WebsocketContext, myId: Long, isJson: Boolean) =     launch {
    repo.observeRoomJoinRequestEvent(myId).collect { event ->
        if (isJson) {
            ws.sendText(Json.encodeToString<UserEvent>(event))
        } else {
            ws.sendText(createHTML().apply {
                when (event) {
                    is RoomJoinRequestEvent.Initial -> roomJoinRequestInitial(event.requests)
                    is RoomJoinRequestEvent.Added -> roomJoinRequestAdded(event.request)
                    is RoomJoinRequestEvent.Removed -> roomJoinRequestRemoved(event.requestId)
                }
            }.finalize())
        }
    }
}


context(repo: Repository)
private fun CoroutineScope.forwardVoiceSignaling(ws: WebsocketContext, myId: Long, connectionId: String) = launch(start = CoroutineStart.UNDISPATCHED) {
    fun negotiateWithJoiningParticipants(roomId: Long) = launch {
        currentLogger().info("[voice] watching joins: room=$roomId user=$myId")
        val negotiationsByUserId = mutableMapOf<Long, Job>()

        launch {
            while (true) {
                val departedUserId = repo.awaitParticipantLeave(roomId)
                negotiationsByUserId[departedUserId]?.cancel()
                negotiationsByUserId.remove(departedUserId)
            }
        }

        while(true) {
            val senderUserId = repo.awaitParticipantJoin(roomId)

            negotiationsByUserId[senderUserId]?.cancelAndJoin()

            negotiationsByUserId[senderUserId] = launch {
                withTimeout(30.seconds) {
                    currentLogger().info("[voice] requesting offer: room=$roomId from=$myId to=${senderUserId}")
                    ws.sendText(Json.encodeToString<UserEvent>(VoiceSignalingEvent.OfferRequested(roomId, senderUserId)))
                    currentLogger().info("[voice] waiting answer: room=$roomId from=${senderUserId} to=$myId")
                    val answer = repo.awaitAnswerFrom(roomId, recipientConnectionId = connectionId, senderUserId = senderUserId)
                    currentLogger().info("[voice] forwarding answer: room=$roomId from=${senderUserId} to=$myId")
                    ws.sendText(Json.encodeToString<UserEvent>(VoiceSignalingEvent.AnswerReceived(roomId, senderUserId, answer.description)))
                }
            }.also {
                it.invokeOnCompletion {
                    negotiationsByUserId.remove(senderUserId)
                }
            }
        }
    }

    fun forwardIncomingOffers(roomId: Long) = launch {
        while (true) {
            val (senderUserId, offer) = repo.awaitIncomingOffer(roomId, connectionId)
            currentLogger().info("[voice] forwarding offer: room=$roomId from=${senderUserId} to=$myId")
            ws.sendText(Json.encodeToString<UserEvent>(VoiceSignalingEvent.OfferReceived(roomId, senderUserId, offer.description)))
        }
    }

    fun forwardIncomingIceCandidates(roomId: Long) = launch {
        while (true) {
            val (senderUserId, candidate) = repo.awaitIncomingIceCandidate(roomId, connectionId)
            currentLogger().info("[voice] forwarding candidate: room=$roomId from=${senderUserId} to=$myId")
            ws.sendText(Json.encodeToString<UserEvent>(VoiceSignalingEvent.IceCandidateReceived(roomId, senderUserId, candidate.iceCandidate)))
        }
    }

    var negotiationJob: Job? = null
    var offerJob: Job? = null
    var iceCandidateJob: Job? = null
    var currentRoomId: Long? = null
    repo.observeVoiceMembershipChanges(connectionId).collect { (roomId, message) ->
        if (message is VoiceCallMessage.JoinCall) {
            if (currentRoomId == null || roomId != currentRoomId) {
                currentLogger().info("[voice] joined: room=${roomId} user=$myId")
                negotiationJob?.cancelAndJoin()
                offerJob?.cancelAndJoin()
                iceCandidateJob?.cancelAndJoin()

                currentRoomId = roomId
                negotiationJob = negotiateWithJoiningParticipants(roomId)
                offerJob = forwardIncomingOffers(roomId)
                iceCandidateJob = forwardIncomingIceCandidates(roomId)
            }
        } else {
            currentLogger().info("[voice] leaving: room=${roomId} user=$myId")
            negotiationJob?.cancelAndJoin()
            offerJob?.cancelAndJoin()
            iceCandidateJob?.cancelAndJoin()

            currentRoomId = null
            negotiationJob = null
            offerJob = null
            iceCandidateJob = null
        }
    }
}

