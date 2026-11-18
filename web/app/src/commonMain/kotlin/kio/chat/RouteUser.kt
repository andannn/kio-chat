package kio.chat

import kio.chat.components.*
import kio.chat.domain.MessageEvent
import kio.chat.domain.Repository
import kio.chat.domain.VoiceCallMessage
import kio.chat.domain.VoiceSignalingEvent
import kio.chat.domain.VoiceParticipantsEvent
import kio.chat.domain.RoomJoinRequestEvent
import kio.chat.domain.RoomListEvent
import kio.chat.domain.UserEvent
import kio.chat.domain.WebSocketSessionReady
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onSubscription
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

    val currentRoomIds = mutableListOf<Long>()
    val wsSessionId = repo.openWsSession(myId)

    launch {
        try {
            sendText(Json.encodeToString<UserEvent>(WebSocketSessionReady(wsSessionId)))
            while (true) { delay(10.seconds) }
        } finally {
            withContext(NonCancellable) { repo.closeWsSession(wsSessionId) }
        }
    }

    forwardRoomJoinRequest(this@handleObserveUserEvent, myId, isJson)
    forwardRoomListEvent(currentRoomIds, this@handleObserveUserEvent, myId, isJson, selectedRoomId)
    forwardChatEvent(currentRoomIds, this@handleObserveUserEvent, myId, isJson)
    forwardVoiceParticipants(currentRoomIds, this@handleObserveUserEvent, isJson)
    forwardVoiceSignaling(this@handleObserveUserEvent, myId, wsSessionId)
}

context(repo: Repository)
private fun CoroutineScope.forwardVoiceParticipants(
    roomIds: List<Long>,
    ws: WebsocketContext,
    isJson: Boolean,
) = launch(start = CoroutineStart.UNDISPATCHED) {
    repo.observeVoiceParticipantChanges()
        .filter { it.roomId in roomIds }
        .collect { change ->
            val event: VoiceParticipantsEvent = if (change.joined) {
                val member = repo.getRoomMembers(change.roomId)
                    .firstOrNull { it.userId == change.userId } ?: return@collect
                VoiceParticipantsEvent.Added(change.roomId, member)
            } else {
                VoiceParticipantsEvent.Removed(change.roomId, change.userId)
            }
            if (isJson) {
                ws.sendText(Json.encodeToString<UserEvent>(event))
            } else {
                ws.sendText(createHTML().apply { voiceParticipantEvent(event) }.finalize())
            }
        }
}

context(repo: Repository)
private fun CoroutineScope.forwardChatEvent(
    roomIds: List<Long>,
    ws: WebsocketContext,
    myId: Long,
    isJson: Boolean,
) = launch {
    repo.getMessageEventSharedFlow()
        .filterIsInstance<MessageEvent.ChatMessageEvent>()
        .filter { roomIds.contains(it.event.roomId) }
        .collect { (event) ->
            if (isJson) {
                ws.sendText(Json.encodeToString<UserEvent>(event))
            } else {
                ws.sendText(createHTML().apply {
                    chatMessageEvent(event, myId)
                }.finalize())
            }
        }
}

context(repo: Repository)
private fun CoroutineScope.forwardRoomListEvent(
    roomIds: MutableList<Long>,
    ws: WebsocketContext,
    myId: Long,
    isJson: Boolean,
    selectedRoomId: Long?
) = launch {
    flow {
        repo.getMessageEventSharedFlow()
            .onSubscription {
                val requests = repo.getRoomList(myId)
                roomIds.addAll(requests.map { it.id })

                this@flow.emit(RoomListEvent.Initial(requests))
            }
            .filterIsInstance<MessageEvent.RoomsMessage>()
            .filter { it.userId == myId }
            .collect { message ->
                when (val event = message.event) {
                    is RoomListEvent.Added -> if (roomIds.add(event.room.id)) emit(event)
                    is RoomListEvent.Deleted -> if (roomIds.remove(event.roomId)) emit(event)
                    is RoomListEvent.Changed -> if (event.room.id in roomIds) emit(event)
                    is RoomListEvent.Initial -> Unit
                }
            }
    }.collect { event ->
        if (isJson) {
            ws.sendText(Json.encodeToString<UserEvent>(event))
        } else {
            ws.sendText(createHTML().apply {
                when (event) {
                    is RoomListEvent.Initial -> roomListInitial(event.rooms, selectedRoomId)
                    is RoomListEvent.Added -> roomListAdded(event.room)
                    is RoomListEvent.Deleted -> roomListDeleted(event.roomId)
                    is RoomListEvent.Changed -> roomListChanged(event.room)
                }
            }.finalize())
        }
    }
}

context(repo: Repository)
private fun CoroutineScope.forwardRoomJoinRequest(ws: WebsocketContext, myId: Long, isJson: Boolean) = launch {
    val ids = mutableSetOf<Long>()
    flow {
        repo.getMessageEventSharedFlow()
            .onSubscription {
                val requests = repo.getRoomJoinRequests(myId)
                ids.addAll(requests.map { it.id })
                this@flow.emit(RoomJoinRequestEvent.Initial(requests))
            }
            .filterIsInstance<MessageEvent.RoomJoinRequestMessageEvent>()
            .collect { (eventUserId, event) ->
                if (eventUserId != myId) return@collect
                when (event) {
                    is RoomJoinRequestEvent.Added -> if (ids.add(event.request.id)) emit(event)
                    is RoomJoinRequestEvent.Removed -> if (ids.remove(event.requestId)) emit(event)
                    is RoomJoinRequestEvent.Initial -> Unit
                }
            }
    }.collect { event ->
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
private fun CoroutineScope.forwardVoiceSignaling(ws: WebsocketContext, myId: Long, wsSessionId: String) = launch(start = CoroutineStart.UNDISPATCHED) {
    fun negotiateWithJoiningParticipants(roomId: Long) = launch {
        currentLogger().info("[voice] watching joins: room=$roomId user=$myId")
        val negotiationsByUserId = mutableMapOf<Long, Job>()

        launch {
            repo.observeParticipantLeave(roomId).collect { departedUserId ->
                negotiationsByUserId[departedUserId]?.cancelAndJoin()
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
                    val answer = repo.awaitAnswerFrom(roomId, recipientWsSessionId = wsSessionId, senderUserId = senderUserId)
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
        repo.observeIncomingOffer(roomId, wsSessionId).collect {  (senderUserId, offer) ->
            currentLogger().info("[voice] forwarding offer: room=$roomId from=${senderUserId} to=$myId")
            ws.sendText(Json.encodeToString<UserEvent>(VoiceSignalingEvent.OfferReceived(roomId, senderUserId, offer.description)))
        }
    }

    fun forwardIncomingIceCandidates(roomId: Long) = launch {
        repo.observeIncomingIceCandidate(roomId, wsSessionId).collect { (senderUserId, candidate) ->
            currentLogger().info("[voice] forwarding candidate: room=$roomId from=${senderUserId} to=$myId")
            ws.sendText(Json.encodeToString<UserEvent>(VoiceSignalingEvent.IceCandidateReceived(roomId, senderUserId, candidate.iceCandidate)))
        }
    }

    var negotiationJob: Job? = null
    var offerJob: Job? = null
    var iceCandidateJob: Job? = null
    var currentRoomId: Long? = null
    repo.observeVoiceMembershipChanges(wsSessionId).collect { (roomId, message) ->
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

