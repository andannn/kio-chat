package kio.chat

import kio.chat.components.*
import kio.chat.domain.Repository
import kio.chat.domain.RoomMessage
import kio.chat.domain.RoomVoiceEvent
import kio.chat.domain.RoomJoinRequestEvent
import kio.chat.domain.RoomListEvent
import kio.chat.domain.UserChatEvent
import kio.chat.domain.UserEvent
import kio.http.WebsocketContext
import kio.http.sendText
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.html.stream.createHTML
import kotlinx.serialization.json.Json

context(repo: Repository)
internal suspend fun WebsocketContext.handleObserveUserEvent() = coroutineScope {
    val myId = requireSession().userId
    val isJson = call.requestParameters["format"] == "json"
    val selectedRoomId = call.requestParameters["selectedRoomId"]?.toLongOrNull()

    launch {
        fun handlingUserJoinRoom(roomId: Long) = launch {
            while(true) {
                val joined = repo.waitVoiceJoinEventInRoom(roomId)
                sendText(Json.encodeToString<UserEvent>(RoomVoiceEvent.OnRequestSendOfferToUser(roomId, joined.senderUserId)))
                val answer = repo.waitRTCAnswerFromUser(roomId, myId, joined.senderUserId)
                sendText(Json.encodeToString<UserEvent>(RoomVoiceEvent.OnRequestSaveAnswer(
                    roomId,
                    answer.senderUserId,
                    (answer.message as RoomMessage.RTCAnswer).description,
                )))
            }
        }

        fun handlingOffer(roomId: Long) = launch {
            while (true) {
                val offer = repo.waitRTCOfferToUser(roomId, myId)
                sendText(Json.encodeToString<UserEvent>(RoomVoiceEvent.OnRequestSendAnswerToUser(
                    roomId,
                    offer.senderUserId,
                    (offer.message as RoomMessage.RTCOffer).description,
                )))
            }
        }

        fun handlingExchangeCandidate(roomId: Long) = launch {
            while (true) {
                val candidate = repo.waitRTCCandidateToUser(roomId, myId)
                sendText(Json.encodeToString<UserEvent>(RoomVoiceEvent.OnReceiveCandidate(
                    roomId,
                    candidate.senderUserId,
                    (candidate.message as RoomMessage.RTCCandidate).iceCandidate,
                )))
            }
        }

        var handlingUserJoinJob: Job? = null
        var handlingOfferJob: Job? = null
        var handlingExchangeCandidateJob: Job? = null
        repo.observeUserVoiceEvent(myId).collect { event ->
            if (event.message is RoomMessage.VoiceJoin) {
                if (handlingUserJoinJob == null && handlingOfferJob == null && handlingExchangeCandidateJob == null) {
                    handlingUserJoinJob = handlingUserJoinRoom(event.roomId)
                    handlingOfferJob = handlingOffer(event.roomId)
                    handlingExchangeCandidateJob = handlingExchangeCandidate(event.roomId)
                }
            } else {
                handlingUserJoinJob?.cancelAndJoin()
                handlingOfferJob?.cancelAndJoin()
                handlingExchangeCandidateJob?.cancelAndJoin()
                handlingUserJoinJob = null
                handlingOfferJob = null
                handlingExchangeCandidateJob = null
            }
        }
    }

    launch {
        repo.observeRoomJoinRequestEvent(myId).collect { event ->
            if (isJson) {
                sendText(Json.encodeToString<UserEvent>(event))
            } else {
                sendText(createHTML().apply {
                    when (event) {
                        is RoomJoinRequestEvent.Initial -> roomJoinRequestInitial(event.requests)
                        is RoomJoinRequestEvent.Added -> roomJoinRequestAdded(event.request)
                        is RoomJoinRequestEvent.Removed -> roomJoinRequestRemoved(event.requestId)
                    }
                }.finalize())
            }
        }
    }
    launch {
        repo.observeRoomAndChatMessageEventByUser(myId).collect { event ->
            if (isJson) {
                sendText(Json.encodeToString<UserEvent>(event))
            } else {
                sendText(createHTML().apply {
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
}
