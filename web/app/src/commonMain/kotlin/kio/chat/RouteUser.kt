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
import kio.chat.util.html
import kio.chat.util.json
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
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration.Companion.seconds

context(repo: Repository)
internal suspend fun WebsocketContext.handleObserveUserEvent(): Unit = coroutineScope {
    val myId = requireSession().userId
    val isJson = call.requestParameters["format"] == "json"
    currentLogger().info("[voice] WebSocket connected: user=$myId")

    val currentRoomIds = mutableSetOf<Long>()
    val ownedRoomIds = mutableSetOf<Long>()
    val wsSessionId = repo.openWsSession(myId)

    listeningRoomList(currentRoomIds, ownedRoomIds, myId)
    launch {
        try {
            sendText(json<UserEvent>(WebSocketSessionReady(wsSessionId)))
            while (true) delay(20.seconds)
        } finally {
            withContext(NonCancellable) { repo.closeWsSession(wsSessionId) }
        }
    }

    forwardRoomJoinRequest(ownedRoomIds, this@handleObserveUserEvent, isJson)
    forwardRoomListEvent(currentRoomIds, this@handleObserveUserEvent, myId, isJson)
    forwardChatEvent(currentRoomIds, this@handleObserveUserEvent, myId, isJson)
    forwardVoiceParticipants(currentRoomIds, this@handleObserveUserEvent, isJson)
    forwardVoiceSignaling(this@handleObserveUserEvent, myId, wsSessionId)
}

context(repo: Repository)
private fun CoroutineScope.forwardVoiceParticipants(
    roomIds: Set<Long>,
    ws: WebsocketContext,
    isJson: Boolean,
) = launch(start = CoroutineStart.UNDISPATCHED) {
    repo.observeVoiceParticipantChanges()
        .filter { it.roomId in roomIds }
        .collect { change ->
            val event: VoiceParticipantsEvent = if (change.joined) {
                val member = repo.getRoomMemberById(change.roomId, change.userId) ?: return@collect
                VoiceParticipantsEvent.Added(change.roomId, member)
            } else {
                VoiceParticipantsEvent.Removed(change.roomId, change.userId)
            }

            if (isJson) {
                ws.sendText(json<UserEvent>(event))
            } else {
                ws.sendText(html { voiceParticipantEvent(event) })
            }
        }
}

context(repo: Repository)
private fun CoroutineScope.forwardChatEvent(
    roomIds: Set<Long>,
    ws: WebsocketContext,
    myId: Long,
    isJson: Boolean,
) = launch {
    repo.getMessageEventSharedFlow()
        .filterIsInstance<MessageEvent.ChatMessageEvent>()
        .filter { roomIds.contains(it.event.roomId) }
        .collect { (event) ->
            if (isJson) {
                ws.sendText(json<UserEvent>(event))
            } else {
                ws.sendText(html { chatMessageEvent(event, myId) })
            }
        }
}

context(repo: Repository)
private fun CoroutineScope.listeningRoomList(
    roomIds: MutableSet<Long>,
    ownedRoomIds: MutableSet<Long>,
    myId: Long,
) = launch {
    repo.getMessageEventSharedFlow()
        .onSubscription {
            val rooms = repo.getRoomList(myId)
            roomIds.addAll(rooms.map { it.id })
            val owned = repo.getOwnedRoomIds(myId)
            ownedRoomIds.addAll(owned)
        }
        .filterIsInstance<MessageEvent.UserRoomEvent>()
        .filter { it.userId == myId }
        .collect { message ->
            when (message) {
                is MessageEvent.UserRoomAddEvent -> {
                    roomIds.add(message.event.room.id)
                    if (repo.isRoomOwner(message.event.room.id, myId)) {
                        ownedRoomIds.add(message.event.room.id)
                    }
                }
                is MessageEvent.UserRoomDeleteEvent -> {
                    roomIds.remove(message.event.roomId)
                    ownedRoomIds.remove(message.event.roomId)
                }
            }
        }
}

context(repo: Repository)
private fun CoroutineScope.forwardRoomListEvent(roomIds: Set<Long>, ws: WebsocketContext, myId: Long, isJson: Boolean) = launch {
    repo.getMessageEventSharedFlow()
        .collect { message ->
            val userEvent = when (message) {
                is MessageEvent.RoomChangeEvent -> if (roomIds.contains(message.event.room.id)) {
                    message.event
                } else {
                    null
                }
                is MessageEvent.UserRoomAddEvent -> if (message.userId == myId) message.event else null
                is MessageEvent.UserRoomDeleteEvent -> if (message.userId == myId) message.event else null
                else -> null
            }

            if (userEvent != null) {
                if (isJson) {
                    ws.sendText(json<UserEvent>(userEvent))
                } else {
                    ws.sendText(html {
                        when (userEvent) {
                            is RoomListEvent.Added -> roomListAdded(userEvent.room)
                            is RoomListEvent.Deleted -> roomListDeleted(userEvent.roomId)
                            is RoomListEvent.Changed -> roomListChanged(userEvent.room)                        }
                    })
                }
            }
        }
}

context(repo: Repository)
private fun CoroutineScope.forwardRoomJoinRequest(
    ownedRoomIds: Set<Long>,
    ws: WebsocketContext,
    isJson: Boolean,
) = launch {
    repo.getMessageEventSharedFlow()
        .filterIsInstance<MessageEvent.RoomJoinRequestMessageEvent>()
        .filter { it.roomId in ownedRoomIds }
        .collect { (_, event) ->
            if (isJson) {
                ws.sendText(json<UserEvent>(event))
            } else {
                ws.sendText(html {
                    when (event) {
                        is RoomJoinRequestEvent.Added -> roomJoinRequestAdded(event.request)
                        is RoomJoinRequestEvent.Removed -> roomJoinRequestRemoved(event.requestId)
                    }
                })
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
                    ws.sendText(json<UserEvent>(VoiceSignalingEvent.OfferRequested(roomId, senderUserId)))
                    currentLogger().info("[voice] waiting answer: room=$roomId from=${senderUserId} to=$myId")
                    val answer = repo.awaitAnswerFrom(roomId, recipientWsSessionId = wsSessionId, senderUserId = senderUserId)
                    currentLogger().info("[voice] forwarding answer: room=$roomId from=${senderUserId} to=$myId")
                    ws.sendText(json<UserEvent>(VoiceSignalingEvent.AnswerReceived(roomId, senderUserId, answer.description)))
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
            ws.sendText(json<UserEvent>(VoiceSignalingEvent.OfferReceived(roomId, senderUserId, offer.description)))
        }
    }

    fun forwardIncomingIceCandidates(roomId: Long) = launch {
        repo.observeIncomingIceCandidate(roomId, wsSessionId).collect { (senderUserId, candidate) ->
            currentLogger().info("[voice] forwarding candidate: room=$roomId from=${senderUserId} to=$myId")
            ws.sendText(json<UserEvent>(VoiceSignalingEvent.IceCandidateReceived(roomId, senderUserId, candidate.iceCandidate)))
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
